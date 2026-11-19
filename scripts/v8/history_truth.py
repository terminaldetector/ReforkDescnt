#!/usr/bin/env python3
"""Bounded read-only Git/source preservation and explicit historical/runtime provenance."""
import argparse,csv,datetime,hashlib,io,json,os,pathlib,stat,subprocess,zipfile

HISTORY=[
 ('963375bf60830461f338ed440dac1aed1d6b9aae','PR9 integration'),('93baf2a4926428e7a21f515c2927eb3a5a511413','Oct24 recovery'),
 ('74d500677a1008f42f159a6272258319d25ebe02','old Claude default'),('9c8706de29650d45ab29dad19f1680b3b17fcf8a','legacy ordnance'),
 ('0ad53e2e8ef64d996851ee557e8b1b62e1271cbf','legacy megacity'),('60ad79892f3f64aca23f0f67b09dccd59462cb26','legacy reactor rollback'),
 *[(x,'pre-Oct26 source change') for x in ['9e15d8b','0345128','82f3ec1','30523be','8ef153d','b72c735','0ac4ae0','c206342','d0361b1','97142ff','550c87e','f1d7904','18feed8','4c7c696','b43d3c1','46d6433','2414fc1','7ac1005','be0be13','ca85f4e','dd9dea1']],
 ('22f3c10','preserved UFO native source'),('3f60f8b6','Dynamic Boarding source'),('17632cd0','local BVH/constraints source'),('f7ca397a','UFO after1.1.34 branch')]
SOURCE_ROOTS=('src','scripts','build.gradle','gradle.properties','settings.gradle')
MAX_SOURCE_BYTES=4*1024*1024

def digest(data):return hashlib.sha256(data).hexdigest()
def git(repo,*args,check=True):
 p=subprocess.run(['git','--no-optional-locks','--no-replace-objects','-C',str(repo),*args],capture_output=True,env={**os.environ,'GIT_OPTIONAL_LOCKS':'0'})
 if check and p.returncode:raise RuntimeError('read-only git failed: '+args[0]+': '+p.stderr.decode(errors='replace')[:300])
 return p

def refs(repo):
 return dict(line.split(' ',1) for line in git(repo,'for-each-ref','--format=%(refname) %(objectname)').stdout.decode().splitlines())
def worktrees(repo):
 out=[];item={}
 for line in git(repo,'worktree','list','--porcelain').stdout.decode().splitlines()+['']:
  if not line:
   if item:out.append(item);item={}
  else:
   k,_,v=line.partition(' ');item[k]=v
 return out

def source_inventory(repo):
 """Preserve both tracked index layers; collect source additions, never user/save/cache trees."""
 first=refs(repo);trees=worktrees(repo);payload={};rows=[]
 for index,tree in enumerate(trees):
  p=pathlib.Path(tree['worktree']);row={'path':str(p),'registered':tree,'capture_errors':[]}
  if not p.is_dir():row['capture_errors'].append('registered worktree unavailable');rows.append(row);continue
  head=git(p,'rev-parse','HEAD').stdout.decode().strip();before=git(p,'status','--porcelain=v1','-z','--untracked-files=normal').stdout
  row.update(head=head,status_tokens=before.decode(errors='surrogateescape').split('\0')[:-1],tracked_patch_layers={})
  for label,args in [('unstaged',('diff','--binary')),('staged',('diff','--cached','--binary'))]:
   data=git(p,*args).stdout;row['tracked_patch_layers'][label]={'sha256':digest(data),'bytes':len(data)}
   if data:payload[f'worktrees/{index:03d}/{label}.patch']=data
  additions=git(p,'ls-files','--others','--exclude-standard','-z','--',*SOURCE_ROOTS).stdout.split(b'\0');row['untracked_source']=[]
  for raw in filter(None,additions):
   relative=os.fsdecode(raw);path=p/relative;entry={'path':relative}
   info=path.lstat()
   if not stat.S_ISREG(info.st_mode):entry['status']='UNKNOWN_NONREGULAR';row['capture_errors'].append(relative+': not a regular source file')
   elif info.st_size>MAX_SOURCE_BYTES:entry['status']='UNKNOWN_OVERSIZE';row['capture_errors'].append(relative+': exceeds source capture bound')
   else:
    data=path.read_bytes();after=path.stat();entry.update(sha256=digest(data),bytes=len(data),status='CAPTURED')
    if (info.st_ino,info.st_mtime_ns,info.st_size)!=(after.st_ino,after.st_mtime_ns,after.st_size):entry['status']='UNSTABLE';row['capture_errors'].append(relative+': changed during read')
    else:payload[f'worktrees/{index:03d}/untracked-source/{relative}']=data
   row['untracked_source'].append(entry)
  after=git(p,'status','--porcelain=v1','-z','--untracked-files=normal').stdout
  row['stable_head_and_status']=head==git(p,'rev-parse','HEAD').stdout.decode().strip() and before==after
  # An already-dirty file can change M→M without changing porcelain status.
  row['stable_patch_and_source_bytes']=True
  for label,args in [('unstaged',('diff','--binary')),('staged',('diff','--cached','--binary'))]:
   if digest(git(p,*args).stdout)!=row['tracked_patch_layers'][label]['sha256']:row['stable_patch_and_source_bytes']=False
  for entry in row['untracked_source']:
   if entry['status']!='CAPTURED':continue
   path=p/entry['path']
   try:
    if not stat.S_ISREG(path.lstat().st_mode) or digest(path.read_bytes())!=entry['sha256']:row['stable_patch_and_source_bytes']=False
   except OSError:row['stable_patch_and_source_bytes']=False
  rows.append(row)
 last=refs(repo)
 return {'refs':first,'refs_unchanged_at_close':first==last,'worktrees':rows,
         'all_registered_source_captured':first==last and all(r.get('stable_head_and_status') and r.get('stable_patch_and_source_bytes') and not r['capture_errors'] for r in rows),
         'preservation_scope':'all registered Git refs/worktrees, both tracked patch layers and untracked source/config additions; untracked work/save/cache/user artifacts remain in place and are not copied'},payload

def historical(repo,source,entries=HISTORY):
 rows=[]
 for requested,component in entries:
  resolved=git(repo,'rev-parse','--verify',requested+'^{commit}',check=False)
  if resolved.returncode:rows.append({'requested':requested,'component':component,'status':'NOT_PROVIDED_OR_AMBIGUOUS'});continue
  commit=resolved.stdout.decode().strip();ancestor=git(repo,'merge-base','--is-ancestor',commit,source,check=False)
  if ancestor.returncode not in (0,1):raise RuntimeError('cannot determine historical ancestry')
  meta=git(repo,'show','-s','--format=%cI%n%s',commit).stdout.decode().splitlines()
  rows.append({'requested':requested,'commit':commit,'component':component,'status':'ANCESTOR_SOURCE' if ancestor.returncode==0 else 'RETAINED_PARALLEL_HISTORY','date':meta[0],'subject':meta[1],
               'merge_base':git(repo,'merge-base',commit,source,check=False).stdout.decode().strip() or None,
               'runtime_acceptance':'NOT_INHERITED; historical code/runtime claims must remain source-specific'})
 return rows

def snapshot(repo,source,prefix='dist/snapshots/2026-11-18-9226e7a0'):
 read=lambda path:git(repo,'show',source+':'+path).stdout
 package=json.loads(read(prefix+'/PACKAGE.json'));manifest=json.loads(read(prefix+'/MANIFEST.json'));delivery=json.loads(read(prefix+'/DELIVERY.json'))
 names=git(repo,'ls-tree','-r','--name-only',source,'--',prefix).stdout.decode().splitlines();jars=[p for p in names if p.endswith('.jar')]
 if len(jars)!=1:raise ValueError('expected one installable snapshot JAR')
 data=read(jars[0]);actual=digest(data)
 if package.get('gameSHA256')!=actual or delivery.get('gameSHA256')!=actual:raise ValueError('snapshot JAR hash differs from declared package/delivery')
 with zipfile.ZipFile(io.BytesIO(data)) as z:mod=json.loads(z.read('fabric.mod.json'))
 built=git(repo,'rev-parse','--verify',package['source']+'^{commit}').stdout.decode().strip()
 delta=git(repo,'diff','--name-only',built,source,'--','src/main').stdout.decode().splitlines()
 return {'published_source':built,'repository_audit_source':source,'installable_path':jars[0],'jar_sha256':actual,'jar_bytes':len(data),'mod':{k:mod.get(k) for k in ['id','version','environment','depends']},
         'production_source_delta_since_snapshot':delta,'production_sources_unchanged':not delta,
         'metadata_blob_sha256':{n:digest(read(prefix+'/'+n)) for n in ['PACKAGE.json','MANIFEST.json','DELIVERY.json','VALIDATION.json']},
         'runtime_scope':'last delivered immutable snapshot/component evidence, not a live user installation or a fresh execution',
         'telegram':{k:delivery.get(k) for k in ['changelog','build','actualScreenshots']},'release_accepted':False}

def java_observation():
 result=[];unreadable=0
 for p in pathlib.Path('/proc').iterdir():
  if not p.name.isdigit():continue
  try:
   comm=(p/'comm').read_text().strip()
   if comm not in ('java','javac','java21'):continue
   argv=(p/'cmdline').read_bytes().split(b'\0')
   result.append({'pid':int(p.name),'kind':comm,'minecraft_or_drmd_argument_present':any(b'minecraft' in a.lower() or b'drmd' in a.lower() for a in argv)})
  except (OSError,UnicodeError):unreadable+=1
 return {'observed_java':result,'unreadable_process_entries':unreadable,'scope':'point-in-time local process read only; no full argv/tokens collected; remote/user processes are unknown'}

def capture(repo,source,output):
 output=pathlib.Path(output);output.mkdir(parents=True,exist_ok=False)
 source=git(repo,'rev-parse','--verify',source+'^{commit}').stdout.decode().strip();inventory,payload=source_inventory(repo)
 history=historical(repo,source);artifact=snapshot(repo,source);handoff={}
 for p in ['docs/v8/STATE.md','docs/v8/DECISIONS.md','docs/v8/REGRESSIONS.md','docs/v8/EVIDENCE/index.json','docs/v8/REMAINING_WORK_2026-11-18.md','docs/v8/IMPLEMENTATION_EMBEDDED_PS1_FIXED_GROUPS_2026-11-18.md']:
  data=git(repo,'show',source+':'+p).stdout;handoff[p]={'bytes':len(data),'git_blob_sha256':digest(data)}
  if p.endswith('/index.json'):
   d=json.loads(data);handoff[p].update(releaseAccepted=d.get('releaseAccepted'),indexed_files=len(d.get('files',{})))
 result={'schema':1,'observed_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'source':source,'inventory':inventory,'history':history,'artifact':artifact,'handoff_documents':handoff,'local_java_observation':java_observation(),
         'unknowns':['exact user-running1.1.14-audit.4 source/JAR not supplied','model-agent identity behind external Sol description is not a Git identity','not all historical runtime assertions are current acceptance','untracked data/save/cache/user files are preserved in place, not copied','no full legacy deletion/use/save lifecycle approval','no new JVM or current full-suite/performance proof']}
 payload['truth.json']=(json.dumps(result,indent=2,ensure_ascii=True)+'\n').encode()
 manifest={'source':source,'payload':{n:{'sha256':digest(b),'bytes':len(b)} for n,b in payload.items()}}
 archive=output/'preserved-source-state.zip'
 with zipfile.ZipFile(archive,'x',zipfile.ZIP_DEFLATED) as z:
  for name,data in payload.items():z.writestr(name,data)
  z.writestr('MANIFEST.json',json.dumps(manifest,indent=2)+'\n')
 with zipfile.ZipFile(archive) as z:
  for name,row in manifest['payload'].items():
   data=z.read(name)
   if digest(data)!=row['sha256'] or len(data)!=row['bytes']:raise ValueError('archive payload mismatch')
 (output/'truth.json').write_bytes(payload['truth.json'])
 summary={'source':source,'archive_sha256':digest(archive.read_bytes()),'archive_bytes':archive.stat().st_size,'payloads':len(payload),'refs':len(inventory['refs']),'worktrees':len(inventory['worktrees']),'source_capture_complete':inventory['all_registered_source_captured'],'historical_entries':len(history),'historical_objects_found':sum('commit' in r for r in history),'snapshot_jar_sha256':artifact['jar_sha256'],'live_user_runtime':'UNKNOWN','new_java_runs':0}
 (output/'summary.json').write_text(json.dumps(summary,indent=2)+'\n');return result,summary

def main():
 p=argparse.ArgumentParser();p.add_argument('--repo',type=pathlib.Path,required=True);p.add_argument('--source',required=True);p.add_argument('--output',type=pathlib.Path,required=True);a=p.parse_args();_,summary=capture(a.repo,a.source,a.output);print(json.dumps(summary,indent=2))
if __name__=='__main__':main()
