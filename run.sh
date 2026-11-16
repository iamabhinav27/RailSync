#!/bin/sh
# Starts RailSync. Arguments: (none)=JavaFX UI, --cli, --simulate, --demo, --awt, --test
cd "$(dirname "$0")" || exit 1
FX="${JAVAFX_HOME:+$JAVAFX_HOME/lib}"
[ -z "$FX" ] && [ -d lib/javafx-sdk/lib ] && FX=lib/javafx-sdk/lib
[ -z "$FX" ] && [ -d /usr/share/openjfx/lib ] && FX=/usr/share/openjfx/lib
[ -f bin/com/railsync/Main.class ] || ./build.sh || exit 1
exec java --module-path "$FX" --add-modules javafx.controls -cp "bin:lib/*" com.railsync.Main "$@"
