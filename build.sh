#!/bin/sh
# Compiles RailSync. JavaFX SDK: export JAVAFX_HOME=/path/to/javafx-sdk (folder containing "lib").
# Falls back to lib/javafx-sdk, then to the Linux package location /usr/share/openjfx.
cd "$(dirname "$0")" || exit 1
FX="${JAVAFX_HOME:+$JAVAFX_HOME/lib}"
[ -z "$FX" ] && [ -d lib/javafx-sdk/lib ] && FX=lib/javafx-sdk/lib
[ -z "$FX" ] && [ -d /usr/share/openjfx/lib ] && FX=/usr/share/openjfx/lib
if [ -z "$FX" ]; then echo "[ERROR] JavaFX SDK not found. Set JAVAFX_HOME (see README)."; exit 1; fi
mkdir -p bin
find src -name '*.java' > sources.txt
javac -encoding UTF-8 --module-path "$FX" --add-modules javafx.controls -cp "lib/*" -d bin @sources.txt
status=$?
rm -f sources.txt
[ $status -ne 0 ] && echo "[ERROR] Compilation failed." && exit $status
echo "Build successful."
