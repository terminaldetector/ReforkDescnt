#!/usr/bin/env python3
"""Static model/resource graph. Runtime item registration and appearance need the native atlas."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import re

CONTEXTS={'gui','ground','fixed','head','firstperson_righthand','firstperson_lefthand','thirdperson_righthand','thirdperson_lefthand'}


def scan(root):
    assets=root/'src/main/resources/assets'
    models={};errors=[];external=set();groups={};rows=[]
    for path in sorted(assets.glob('*/models/**/*.json')):
        relative=path.relative_to(assets);key=relative.parts[0]+':'+Path(*relative.parts[2:]).with_suffix('').as_posix()
        try:
            data=json.loads(path.read_text());assert isinstance(data,dict)
            models[key]=(path,data)
        except (ValueError,AssertionError) as error:errors.append(f'{path.relative_to(root)}: invalid model JSON: {error}')
    def parse_id(value,default='minecraft'):
        namespace,sep,path=value.partition(':')
        return (namespace,path) if sep else (default,namespace)
    def inherited(key,chain=()):
        if key in chain:raise ValueError('cyclic model parent: '+' -> '.join(chain+(key,)))
        if key not in models:
            namespace,_=parse_id(key)
            if namespace=='minecraft':external.add('model '+key);return {}
            raise ValueError('missing model '+key)
        _,data=models[key];result={}
        if 'parent' in data:
            ns,path=parse_id(data['parent']);result.update(inherited(ns+':'+path,chain+(key,)))
        result.update(data.get('textures',{}));return result
    for key,(path,data) in models.items():
        relative=path.relative_to(root).as_posix();issue_before=len(errors)
        try:textures=inherited(key)
        except (ValueError,TypeError) as error:errors.append(relative+': '+str(error));textures={}
        def texture(value,seen=()):
            if not isinstance(value,str):raise ValueError('non-string texture reference')
            if value.startswith('#'):
                name=value[1:]
                if name in seen or name not in textures:raise ValueError('unresolved/cyclic texture '+value)
                return texture(textures[name],seen+(name,))
            namespace,ref=parse_id(value)
            if namespace=='minecraft':external.add('texture '+value);return
            target=assets/namespace/'textures'/(ref+'.png')
            if '..' in Path(ref).parts or not target.is_file():raise ValueError('missing texture '+value)
            if target.read_bytes()[:8]!=b'\x89PNG\r\n\x1a\n':raise ValueError('non-PNG texture '+value)
        references=list(data.get('textures',{}).values())
        for element in data.get('elements',[]):
            for face in element.get('faces',{}).values():references.append(face.get('texture'))
            for vector in ('from','to'):
                values=element.get(vector)
                if not isinstance(values,list) or len(values)!=3 or any(not isinstance(v,(int,float)) or not math.isfinite(v) or not -16<=v<=32 for v in values):
                    errors.append(relative+': invalid element '+vector)
        for value in references:
            try:texture(value)
            except ValueError as error:errors.append(relative+': '+str(error))
        for context,transform in data.get('display',{}).items():
            if context not in CONTEXTS:errors.append(relative+': unknown display '+context)
            for name,values in transform.items():
                if name not in ('rotation','translation','scale') or not isinstance(values,list) or len(values)!=3 or any(not isinstance(v,(int,float)) or not math.isfinite(v) for v in values):
                    errors.append(relative+': invalid display '+context+'/'+name)
        if ':item/' in key:
            fingerprint=hashlib.sha256(json.dumps({k:data[k] for k in ('parent','elements','display') if k in data},sort_keys=True).encode()).hexdigest()
            groups.setdefault(fingerprint,[]).append(key)
            rows.append(dict(id=key.split(':item/',1)[1],model=relative,parent=data.get('parent',''),status='valid-static' if issue_before==len(errors) else 'broken',runtime_registration='NOT VERIFIED'))
    duplicates=[group for group in groups.values() if len(group)>1]
    return dict(models=len(models),items=rows,errors=sorted(set(errors)),duplicate_geometry=duplicates,vanilla_dependencies=sorted(external))


def legacy(root):
    """Conservative reference candidates; text counts are not proof of dead runtime code."""
    files=list((root/'src').rglob('*.java'));texts={p:p.read_text() for p in files}
    result=[]
    for path in files:
        if '/main/' not in path.as_posix():continue
        name=path.stem;references=[]
        for other,text in texts.items():
            if other!=path and re.search(r'\b'+re.escape(name)+r'\b',text):references.append(other.relative_to(root).as_posix())
        if len(references)<=1:result.append(dict(path=path.relative_to(root).as_posix(),text_references=references,action='review only; no removal',runtime='check Fabric entrypoints/mixins/registry/dynamic loading'))
    return result


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--root',type=Path,default=Path('.'));parser.add_argument('--out',type=Path,default=Path('build/resource-audit'));args=parser.parse_args()
    root=args.root.resolve();report=scan(root);report['legacy_candidates']=legacy(root);args.out.mkdir(parents=True,exist_ok=True)
    (args.out/'resources.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    lines=['# Static resource and legacy audit','','Native registration, textures in the vanilla game, resource reload and rendered appearance: NOT VERIFIED.','',f'Models: {report["models"]}; item model resources: {len(report["items"])}; errors: {len(report["errors"])}.','','| Item model ID | Source | Status |','|---|---|---|']
    lines += [f'| {row["id"]} | {row["model"]} | {row["status"]} |' for row in report['items']]
    lines += ['','## Identical geometry: review warnings','']+['- '+', '.join(group) for group in report['duplicate_geometry']]
    lines += ['','Different textures can intentionally distinguish one shared silhouette. No group is auto-deleted.','','## Low-reference source candidates','']
    lines += ['- '+row['path']+'; references: '+', '.join(row['text_references'])+'; '+row['action'] for row in report['legacy_candidates']]
    lines += ['','Fabric entrypoints, mixin JSON, registry strings, resources, reflection and serialized IDs must be checked before any removal.','','## Errors','']+['- '+error for error in report['errors']]
    (args.out/'REPORT.md').write_text('\n'.join(lines)+'\n')
    print(f'Models={report["models"]}; item resources={len(report["items"])}; errors={len(report["errors"])}; duplicate groups={len(report["duplicate_geometry"])}')
    for error in report['errors']:print(error)
    return bool(report['errors'])

if __name__=='__main__':raise SystemExit(main())
