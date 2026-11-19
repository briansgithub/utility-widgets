#!/bin/bash
# editor.sh: open the coded test photo in Photos > Edit > Crop > Straighten (fresh, unsaved)
A=$(dirname "$0")/adb.sh
$A shell "am force-stop com.google.android.apps.photos; am start -a android.intent.action.VIEW -d content://media/external/images/media/${MEDIA:?run setup.sh and pass MEDIA} -t image/png -p com.google.android.apps.photos >/dev/null"
tapdesc() { # tap the centre of the first node whose attr matches
  for i in $(seq 1 20); do
    b=$($A shell 'uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; cat /sdcard/ui.xml' | python3 -I -c "
import sys,re
x=sys.stdin.read()
for m in re.finditer(r'<node [^>]*>',x):
    n=m.group(0)
    if re.search(sys.argv[1],n):
        b=re.search(r'bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"',n).groups(); b=list(map(int,b))
        print((b[0]+b[2])//2,(b[1]+b[3])//2); break" "$1")
    [ -n "$b" ] && { $A shell input tap $b; return 0; }
    sleep 0.5
  done; echo "missing $1"; return 1
}
tapdesc 'content-desc="Edit"' && sleep 2 && tapdesc 'text="Crop" [^>]*clickable="true"' && sleep 1.5 && tapdesc 'text="Straighten"' && sleep 1.2
$(dirname "$0")/probe.sh ed$RANDOM state | grep -o 'value=[^ ]*'
