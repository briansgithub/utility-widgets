#!/bin/bash
# panb.sh <Up|Down|Left|Right> : fresh editor, tap the real pad button once, export, decode along that axis
b=$1
case $b in Up|Down) axis=y;; *) axis=x;; esac
export AXIS=$axis DECODER=$(dirname "$0")/decq.py EXPORT=ftq$axis
. "${FT_OUT:-/tmp/photos-crop}/ids.env"; [ $axis = x ] && export MEDIA=$FTQX || export MEDIA=$FTQY
$(dirname "$0")/editor.sh >/dev/null; sleep 0.5
xy=$($(dirname "$0")/pad.sh $b) || exit 1
$(dirname "$0")/adb.sh logcat -c
$(dirname "$0")/adb.sh shell input tap $xy
for i in $(seq 1 30); do
  n=$($(dirname "$0")/adb.sh logcat -d -s FTProbe:I | grep -c "pad gesture")
  [ "$n" -ge 2 ] && break; sleep 0.3
done
r=$($(dirname "$0")/adb.sh logcat -d -s FTProbe:I | grep -oE "pad (press|gesture) [A-Za-z]+( interrupted=[a-z]+)?" | sed 's/pad //' | tr '\n' ',')
sleep 0.6
$(dirname "$0")/apply_save.sh "$b [$r]" 2>&1 | grep -v pulled
