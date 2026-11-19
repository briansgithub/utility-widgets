#!/bin/bash
# setup.sh: make the two phase-coded test photos, push them to the phone, and record their MediaStore ids
set -e
d=$(cd "$(dirname "$0")" && pwd); out=${FT_OUT:-/tmp/photos-crop}; mkdir -p "$out/img"
for a in x y; do
  [ -f "$out/img/ftq$a.png" ] || uv run -q --with pillow python -I "$d/mkq.py" "$out/img/ftq$a.png" $a
  w=$(command -v wslpath >/dev/null && wslpath -w "$out/img/ftq$a.png" || echo "$out/img/ftq$a.png")
  "$d/adb.sh" push "$w" /sdcard/Pictures/FloatToolsTest/ftq$a.png >/dev/null
  "$d/adb.sh" shell "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Pictures/FloatToolsTest/ftq$a.png" >/dev/null
done
sleep 3
q() { "$d/adb.sh" shell "content query --uri content://media/external/images/media --projection _id --where \"_display_name='$1'\"" | grep -o '_id=[0-9]*' | head -1 | cut -d= -f2; }
echo "FTQX=$(q ftqx.png)" > "$out/ids.env"; echo "FTQY=$(q ftqy.png)" >> "$out/ids.env"; cat "$out/ids.env"
