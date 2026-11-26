#!/bin/bash
# tapnode.sh <regex on raw node xml>: tap the centre of the first matching node (retries 10x)
for i in $(seq 1 10); do
  b=$($(dirname "$0")/adb.sh shell 'uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; cat /sdcard/ui.xml' | python3 -I -c "
import sys,re
x=sys.stdin.read()
for m in re.finditer(r'<node [^>]*>',x):
    n=m.group(0)
    if re.search(sys.argv[1],n):
        b=list(map(int,re.search(r'bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"',n).groups()))
        print((b[0]+b[2])//2,(b[1]+b[3])//2); break" "$1")
  [ -n "$b" ] && { $(dirname "$0")/adb.sh shell input tap $b; exit 0; }
  sleep 0.4
done; echo "missing $1" >&2; exit 1
