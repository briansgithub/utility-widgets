#!/bin/bash
# pad.sh <CW|CCW|Up|Down|Left|Right>: screen centre of that crop-pad button, read from the pad window's frame
d=$(dirname "$0")
for i in $(seq 1 12); do
  f=$("$d/adb.sh" shell 'dumpsys window windows' | grep -o 'com.floatplay.overlay, frame=\[Rect([0-9, -]*)' | head -1 | grep -oE '[0-9]+' | tr '\n' ' ')
  read l t r b <<< "$f"
  [ -n "$b" ] && break; sleep 0.4
done
[ -z "$b" ] && { echo "pad not showing" >&2; exit 1; }
k=$(( (r - l) * 52 / 164 ))   # grip bar 28 dp, then 2 rows x 3 columns of 52 dp keys, 4 dp gaps (164 x 140 dp)
c0=$(( l + k / 2 )); c1=$(( (l + r) / 2 )); c2=$(( r - k / 2 )); r0=$(( b - k - (r - l) * 4 / 164 - k / 2 )); r1=$(( b - k / 2 ))
[ $(( r - l )) -lt $(( k * 2 )) ] && { echo "pad is folded; tap it to unfold" >&2; exit 1; }
case $1 in CCW) echo $c0 $r0;; Up) echo $c1 $r0;; CW) echo $c2 $r0;; Left) echo $c0 $r1;; Down) echo $c1 $r1;; Right) echo $c2 $r1;; *) exit 2;; esac
