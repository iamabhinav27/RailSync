@echo off
REM Compiles RailSync. JavaFX SDK: set JAVAFX_HOME (folder that contains "lib") or unzip the SDK to lib\javafx-sdk
setlocal
if "%JAVAFX_HOME%"=="" set JAVAFX_HOME=lib\javafx-sdk
if not exist "%JAVAFX_HOME%\lib" (
  echo [ERROR] JavaFX SDK not found. Download it from https://openjfx.io and set JAVAFX_HOME, e.g.
  echo         set JAVAFX_HOME=C:\javafx-sdk-21
  exit /b 1
)
if not exist bin mkdir bin
dir /s /b src\*.java > sources.txt
javac -encoding UTF-8 --module-path "%JAVAFX_HOME%\lib" --add-modules javafx.controls -cp "lib\*" -d bin @sources.txt
set RESULT=%ERRORLEVEL%
del sources.txt
if %RESULT% neq 0 (echo [ERROR] Compilation failed. & exit /b %RESULT%)
echo Build successful.
