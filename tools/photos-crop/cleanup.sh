#!/bin/bash
# cleanup.sh: delete the test photos (and any leftover exports) from the phone
d=$(dirname "$0")
for id in $("$d/adb.sh" shell "content query --uri content://media/external/images/media --projection _id --where \"_display_name LIKE 'ftq%'\"" | grep -o '_id=[0-9]*' | cut -d= -f2); do
  "$d/adb.sh" shell "content delete --uri content://media/external/images/media/$id" >/dev/null
done
"$d/adb.sh" shell 'rmdir /sdcard/Pictures/FloatToolsTest 2>/dev/null; true'
