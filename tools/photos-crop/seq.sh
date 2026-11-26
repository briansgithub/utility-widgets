#!/bin/bash
# seq.sh <btn> <n> <gap s>: in the editor already open, tap one pad arrow n times gap seconds apart (as a person would),
# then save a copy and decode the position. No uiautomator dump runs between the taps.
d=$(dirname "$0"); b=$1; n=$2; g=$3
case $b in Up|Down) axis=y;; *) axis=x;; esac
export AXIS=$axis DECODER=$d/decq.py EXPORT=ftq$axis
xy=$($d/pad.sh $b) || exit 1; sleep 2; $d/adb.sh logcat -c
for i in $(seq 1 $n); do $d/adb.sh shell input tap $xy; sleep $g; done
sleep 1.5
L=$($d/adb.sh logcat -d -s FTProbe:I)
echo -n "$b x$n gap=$g presses=$(echo "$L" | grep -c 'pad press') gestures=$(echo "$L" | grep -c 'pad gesture') cancelled=$(echo "$L" | grep -c 'interrupted=true\|cancelled') ready=$(echo "$L" | grep -c ' ready ') "
$d/apply_save.sh "" 2>&1 | grep -v pulled
