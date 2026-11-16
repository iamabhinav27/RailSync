@echo off
REM Starts RailSync. Arguments: (none)=JavaFX UI, --cli, --simulate, --demo, --awt
setlocal

if "%JAVAFX_HOME%"=="" set JAVAFX_HOME=lib\javafx-sdk

if not exist bin\com\railsync\Main.class call build.bat || exit /b 1

java --enable-native-access=javafx.graphics --module-path "%JAVAFX_HOME%\lib" --add-modules javafx.controls -cp "bin;lib\*" com.railsync.Main %*