#!/bin/bash
# apply the straighten tool until "Save as copy" shows, then save copy and decode
for i in $(seq 1 8); do
  ui=$($(dirname "$0")/dump.sh)
  echo "$ui" | grep -q "t='Save as copy'" && break
  echo "$ui" | grep -q "d='Apply effect'" && { $(dirname "$0")/tapnode.sh 'content-desc="Apply effect"'; sleep 1.2; continue; }
  sleep 0.7
done
$(dirname "$0")/savecopy.sh "$@"
