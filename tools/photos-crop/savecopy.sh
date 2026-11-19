#!/bin/bash
# tap Save as copy, wait for a new export to appear and settle, pull it to a unique file, decode, delete it on the phone
A=$(dirname "$0")/adb.sh
$(dirname "$0")/tapnode.sh 'text="Save as copy"' || exit 1
for i in $(seq 1 40); do
  sleep 1
  row=$($A shell "content query --uri content://media/external/images/media --projection _id:_data:_size --where \"_display_name LIKE '${EXPORT:-ftcoded}~%' AND is_pending=0\" --sort '_id DESC'" | grep Row | head -1)
  id=$(echo "$row" | grep -o '_id=[0-9]*' | cut -d= -f2)
  [ -n "$id" ] && break
done
[ -z "$id" ] && { echo "${1:-export} no export found"; exit 1; }
path=$(echo "$row" | grep -o '_data=[^,]*' | cut -d= -f2 | tr -d '\r')
sleep 1
f=${FT_OUT:-/tmp/photos-crop}/exports/$(date +%s%N).jpg; mkdir -p ${FT_OUT:-/tmp/photos-crop}/exports
$A pull "$path" "$(command -v wslpath >/dev/null && wslpath -w $f || echo $f)" >/dev/null 2>&1
[ -s "$f" ] || { echo "${1:-export} pull failed"; exit 1; }
echo -n "${1:-export} "; timeout 120 uv run -q --with pillow python -I ${DECODER:-$(dirname "$0")/decq.py} "$f" $AXIS
$A shell "content delete --uri content://media/external/images/media/$id" >/dev/null
