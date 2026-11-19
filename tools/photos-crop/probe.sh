#!/bin/bash
# probe.sh <id> <op> [extra am args...]; waits for and prints the FTProbe line for that id
id=$1; op=$2; shift 2
[ -z "$NOCLEAR" ] && $(dirname "$0")/adb.sh logcat -c
$(dirname "$0")/adb.sh shell am broadcast -a com.floatplay.overlay.PROBE --es op $op --es id $id "$@" >/dev/null
for i in $(seq 1 40); do
  l=$($(dirname "$0")/adb.sh logcat -d -s FTProbe:I | grep " $id " | tail -1)
  [ -n "$l" ] && { echo "$l" | sed 's/.*FTProbe: //'; exit 0; }
  sleep 0.25
done
echo "$id timeout"
