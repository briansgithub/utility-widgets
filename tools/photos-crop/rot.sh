#!/bin/bash
# rot.sh <n> <dx> <dur> <hold> [pre] : n rotation drags, prints per-trial delta in degrees
n=$1; dx=$2; dur=$3; hold=$4; pre=${5:-0}
for i in $(seq 1 $n); do
  l=$($(dirname "$0")/probe.sh r$i$RANDOM rot --ef dx $dx --ei dur $dur --ei hold $hold --ei pre $pre --ei settle 600)
  python3 -I -c "
import re,sys,math
l=sys.argv[1]
b=re.search(r'before=(-?[\d.E-]+)',l); a=re.search(r'value=(-?[\d.E-]+)',l); r=re.search(r'gesture (\w+)',l)
print('%s %+.3f°(%s)'%(r.group(1)[0], math.degrees(float(a.group(1))-float(b.group(1))), round(math.degrees(float(a.group(1))),2)) if a and b else l)" "$l"
done | tr '\n' ' '; echo
