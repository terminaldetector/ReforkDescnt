from pathlib import Path
import os,json,hashlib,sys
root=Path.cwd();runtime=root/'build/cubic-client-automation';donor=Path('/workspace/ungravity-v5/boarding-attachment/build/cubic-client-final-body-scale-volumes-final-gl32/libraries');records=[]
for p in (runtime/'libraries').rglob('*.jar'):
 source=donor/p.relative_to(runtime/'libraries')
 if not source.is_file() or os.path.samefile(p,source):continue
 digest=hashlib.sha256(p.read_bytes()).hexdigest();assert digest==hashlib.sha256(source.read_bytes()).hexdigest()
 size=p.stat().st_size;temporary=p.with_suffix('.verified-link');os.link(source,temporary);os.replace(temporary,p)
 records.append(dict(path=str(p.relative_to(runtime)),donor=str(source),bytes=size,sha256=digest))
(root/('work/body-joints/native/'+sys.argv[1]+'-library-dedup.json')).write_text(json.dumps(records,indent=2)+'\n')
print('Verified immutable libraries before launch:',len(records),'files')
