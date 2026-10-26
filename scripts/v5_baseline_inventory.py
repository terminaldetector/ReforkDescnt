import json,hashlib,gzip,re,subprocess,zipfile,argparse
from pathlib import Path
parser=argparse.ArgumentParser(description="Read-only history/binary/log inventory; does not certify runtime acceptance")
parser.add_argument('--repo',type=Path,required=True)
parser.add_argument('--packet',type=Path,required=True)
parser.add_argument('--archive',type=Path,required=True)
parser.add_argument('--out',type=Path,required=True)
args=parser.parse_args()
root=args.repo.resolve();packet=args.packet.resolve();out=args.out.resolve();out.mkdir(parents=True,exist_ok=True)
def git(*a):return subprocess.run(['git','-C',str(root),*a],capture_output=True,text=True).stdout.strip()
audit=(packet/'04_AGENT_AUDIT/PRE_OCT26_AGENT_WORK_AUDIT.md').read_text()
commits=[]
for sha in dict.fromkeys(re.findall(r'`([0-9a-f]{7,40})`',audit)):
 full=git('rev-parse','--verify',sha+'^{commit}')
 if full:
  ancestor=subprocess.run(['git','-C',str(root),'merge-base','--is-ancestor',full,'HEAD']).returncode==0
  files=git('diff-tree','--no-commit-id','--name-only','-r',full).splitlines()
  commits.append(dict(reference=sha,sha=full,in_current_ancestry=ancestor,subject=git('show','-s','--format=%s',full),files=files,current_runtime='UNVERIFIED'))
 else:commits.append(dict(reference=sha,current_runtime='UNVERIFIED',local_commit='MISSING'))
logs=[];seen={}
for p in sorted((packet/'07_EVIDENCE/logs').iterdir()):
 data=gzip.decompress(p.read_bytes()) if p.suffix=='.gz' else p.read_bytes();sha=hashlib.sha256(data).hexdigest();s=data.decode(errors='replace');lag=[int(x) for x in re.findall(r'Running (\d+)ms',s)]
 logs.append(dict(file=p.name,content_sha256=sha,duplicate_of=seen.get(sha),lag_alerts=len(lag),max_alerted_lag_ms=max(lag,default=0),dummy=s.count('Tried to load a DUMMY'),mirror=s.count('[drmd view] mirror:')));seen.setdefault(sha,p.name)
jars=[]
for name in sorted(str(p) for p in (root/'build/libs').glob('*.jar') if not any(x in p.name for x in ['-sources','-dev'])):
 p=root/name
 if p.exists():
  with zipfile.ZipFile(p) as z:metadata=json.loads(z.read('fabric.mod.json'))
  jars.append(dict(path=str(p),sha256=hashlib.sha256(p.read_bytes()).hexdigest(),version=metadata['version'],user_tlauncher_binary=False))
result=dict(base_sha=git('rev-parse','HEAD'),branch=git('branch','--show-current'),status=git('status','--porcelain=v1'),historical_commits=commits,jars=jars,logs=logs,packet_sha256=hashlib.sha256(args.archive.read_bytes()).hexdigest(),unique_log_sessions=len(seen),runtime_provenance='TLauncher JAR checksum and original saves not supplied; user log version label is not binary identity')
(out/'baseline.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
lines=['# Historical fixes: local ancestry inventory','', '| Reference | Present in HEAD ancestry | Historical change | Current runtime |','|---|---|---|---|']
for c in commits:lines.append(f"| {c['reference']} | {c.get('in_current_ancestry','MISSING')} | {c.get('subject','missing local object')} | UNVERIFIED under v5 acceptance |")
(out/'HISTORICAL_MATRIX.md').write_text('\n'.join(lines)+'\n')
print('Inventory:',len(commits),'historical references;',len(seen),'unique sessions;',len(jars),'local binaries')
