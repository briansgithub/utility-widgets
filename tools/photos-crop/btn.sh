#!/bin/bash
# btn.sh <n> <x> <y> : tap the pad button with adb input n times, print angle delta per tap (degrees)
n=$1; x=$2; y=$3
prev=$($(dirname "$0")/probe.sh p0$RANDOM state | grep -o 'value=[-0-9.E]*' | cut -d= -f2)
for i in $(seq 1 $n); do
  $(dirname "$0")/adb.sh shell "input tap $x $y"; sleep ${GAP:-1.2}
  cur=$($(dirname "$0")/probe.sh p$i$RANDOM state | grep -o 'value=[-0-9.E]*' | cut -d= -f2)
  python3 -I -c "import math,sys; print('%+.3f'%math.degrees(float(sys.argv[2])-float(sys.argv[1])),end=' ')" $prev $cur
  prev=$cur
done; echo " (now $(python3 -I -c "import math;print(round(math.degrees($prev),2))"))"
