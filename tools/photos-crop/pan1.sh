#!/bin/bash
# pan1.sh <axis x|y> <signed px> [dur] [pinch] [cx] [cy]: one fresh editor, minimal pinch at the photo centre, one drag (0 = baseline), export, decode
axis=$1; d=$2; dur=${3:-220}; ph=${4:-22}; cx=${5:-540}; cy=${6:-1123}
export AXIS=$axis DECODER=$(dirname "$0")/decq.py EXPORT=ftq$axis
. "${FT_OUT:-/tmp/photos-crop}/ids.env"; [ $axis = x ] && export MEDIA=$FTQX || export MEDIA=$FTQY
$(dirname "$0")/editor.sh >/dev/null
$(dirname "$0")/probe.sh z$RANDOM pinch --ef x $cx --ef y $cy --ef gap 63 --ef half $ph --ei dur 280 --ei settle 400 >/dev/null
r=-
if [ "$d" != 0 ]; then
  [ $axis = x ] && args="--ef dx $d --ef dy 0" || args="--ef dx 0 --ef dy $d"
  r=$($(dirname "$0")/probe.sh p$RANDOM drag --ef x 400 --ef y $cy $args --ei dur $dur $EXTRA --ei settle 400 | grep -o 'gesture [a-z]*' | cut -c9)
fi
$(dirname "$0")/apply_save.sh "$axis d=$d dur=$dur [$r]" 2>&1 | grep -v pulled
