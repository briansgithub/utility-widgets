#!/bin/bash
# pad.sh <CW|CCW|Up|Down|Left|Right>: screen centre of that crop-pad button, read from the pad window's frame
d=$(dirname "$0")
for i in $(seq 1 12); do
  f=$("$d/adb.sh" shell 'dumpsys window windows' | grep -o 'com.floatplay.overlay, frame=\[Rect([0-9, -]*)' | head -1 | grep -oE '[0-9]+' | tr '\n' ' ')
  read l t r b <<< "$f"
  [ -n "$b" ] && break; sleep 0.4
done
[ -z "$b" ] && { echo "pad not showing" >&2; exit 1; }
cw=$(( (r - l) * 52 / 110 ))   # 52 dp buttons, 6 dp gap, 2 columns x 3 rows
c0=$(( l + cw / 2 )); c1=$(( r - cw / 2 )); r0=$(( t + cw / 2 )); r1=$(( (t + b) / 2 )); r2=$(( b - cw / 2 ))
case $1 in CW) echo $c0 $r0;; CCW) echo $c1 $r0;; Up) echo $c0 $r1;; Down) echo $c1 $r1;; Left) echo $c0 $r2;; Right) echo $c1 $r2;; *) exit 2;; esac
