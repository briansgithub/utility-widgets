#!/bin/bash
# burst.sh <n> <x> <y> <gap_s>: n quick taps in one adb shell, prints total angle delta in steps of 0.01 rad
n=$1; x=$2; y=$3; g=$4
v0=$($(dirname "$0")/probe.sh b0$RANDOM state | grep -o 'value=[-0-9.E]*' | cut -d= -f2)
$(dirname "$0")/adb.sh shell "for i in \$(seq 1 $n); do input tap $x $y; sleep $g; done"; sleep 1.5
v1=$($(dirname "$0")/probe.sh b1$RANDOM state | grep -o 'value=[-0-9.E]*' | cut -d= -f2)
python3 -I -c "import sys; print('taps=$n gap=$g steps=%.2f'%((float(sys.argv[2])-float(sys.argv[1]))/0.01))" $v0 $v1
