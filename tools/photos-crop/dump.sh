#!/bin/bash
# dump UI hierarchy to ${FT_OUT:-/tmp/photos-crop}/ui.xml, print compact node list
mkdir -p "${FT_OUT:-/tmp/photos-crop}"
$(dirname "$0")/adb.sh shell 'uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; cat /sdcard/ui.xml' > ${FT_OUT:-/tmp/photos-crop}/ui.xml
python3 -I - ${FT_OUT:-/tmp/photos-crop}/ui.xml "$@" <<'PY'
import sys,re,xml.etree.ElementTree as ET
t=ET.parse(sys.argv[1])
pat=sys.argv[2] if len(sys.argv)>2 else None
for n in t.iter('node'):
    a=n.attrib
    s=f"{a.get('class','').split('.')[-1]} id={a.get('resource-id','').split('/')[-1]} t={a.get('text','')!r} d={a.get('content-desc','')!r} {a.get('bounds')} {'C' if a.get('clickable')=='true' else ''}{'S' if a.get('scrollable')=='true' else ''}"
    if a.get('text') or a.get('content-desc') or a.get('resource-id') or a.get('clickable')=='true':
        if not pat or re.search(pat,s,re.I): print(s)
PY
