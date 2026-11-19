#!/bin/bash
# panburst.sh <button> <n> <gap>: fresh editor, n quick taps on one pad button, wait for the queue, export
b=$1; n=$2; g=$3
case $b in Up|Down) axis=y;; *) axis=x;; esac
export AXIS=$axis DECODER=$(dirname "$0")/decq.py EXPORT=ftq$axis
. "${FT_OUT:-/tmp/photos-crop}/ids.env"; [ $axis = x ] && export MEDIA=$FTQX || export MEDIA=$FTQY
$(dirname "$0")/editor.sh >/dev/null; sleep 0.5
xy=$($(dirname "$0")/pad.sh $b) || exit 1; $(dirname "$0")/adb.sh logcat -c
$(dirname "$0")/adb.sh shell "for i in \$(seq 1 $n); do input tap $xy; sleep $g; done"
for i in $(seq 1 40); do
  c=$($(dirname "$0")/adb.sh logcat -d -s FTProbe:I | grep -c "pad gesture completed interrupted=false")
  [ "$c" -ge $((n+1)) ] && break; sleep 0.4
done; sleep 0.8
L=$($(dirname "$0")/adb.sh logcat -d -s FTProbe:I)
echo -n "$b x$n gap=$g presses=$(echo "$L" | grep -c 'pad press') ok=$(echo "$L" | grep -c 'interrupted=false') retried=$(echo "$L" | grep -c 'interrupted=true\|gesture cancelled') "
$(dirname "$0")/apply_save.sh "" 2>&1 | grep -v pulled
