#!/bin/bash
# adb for the phone under test: ADB (path to adb or adb.exe) and SERIAL (adb -s value) must be set
cd /tmp
exec "${ADB:?set ADB to the adb binary}" -s "${SERIAL:?set SERIAL to the phone serial}" "$@"
