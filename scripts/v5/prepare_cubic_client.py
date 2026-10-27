#!/usr/bin/env python3
"""Stage an unmodified production Fabric/BCC client; never remap the closed upstream JAR.

All generated files live below the explicitly supplied runtime directory. Original server saves
and source branches are left alone. A stage manifest proves inputs, not a successful client test.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import sys
import urllib.request


def digest(path, algorithm='sha256'):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, algorithm).hexdigest()


def linux_library(library):
    rules=library.get('rules')
    if rules is None:
        return True
    allowed=False
    for rule in rules:
        platform=rule.get('os',{})
        if platform.get('name','linux')!='linux':
            continue
        if 'arch' in platform and platform['arch'] not in ('x86_64','amd64'):
            continue
        if platform.get('version') or any(rule.get('features',{}).values()):
            continue
        allowed=rule['action']=='allow'
    return allowed


def artifact_path(root, relative):
    p=PurePosixPath(relative)
    if p.is_absolute() or '..' in p.parts:
        raise ValueError(f'Unsafe artifact path: {relative}')
    return root.joinpath(*p.parts)


def verified_copy(source, target, sha1=None):
    if sha1 and digest(source,'sha1')!=sha1:
        raise ValueError(f'Checksum mismatch: {source}')
    target.parent.mkdir(parents=True,exist_ok=True)
    if source.resolve()!=target.resolve():
        shutil.copy2(source,target)
    return target


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cache',type=Path,default=Path('/workspace/.gradle'))
    parser.add_argument('--server-runtime',type=Path,required=True)
    parser.add_argument('--runtime',type=Path,required=True)
    parser.add_argument('--drmd',type=Path,required=True)
    parser.add_argument('--bcc',type=Path,required=True)
    parser.add_argument('--api',type=Path,required=True)
    parser.add_argument('--gate',type=Path,help='Separate remapped test-only cubic client harness')
    parser.add_argument('--sodium',type=Path,help='Optional exact Sodium A/B runtime artifact')
    parser.add_argument('--java',type=Path,required=True)
    parser.add_argument('--copy-test-world',action='store_true')
    parser.add_argument('--download-missing',action='store_true')
    args=parser.parse_args()
    runtime=args.runtime.resolve()
    if runtime.exists() and any(runtime.iterdir()):
        raise ValueError('Use a new empty runtime directory; do not overwrite an existing test/save')
    runtime.mkdir(parents=True,exist_ok=True)
    metadata=args.cache/'caches/fabric-loom/1.21.1/mojang_minecraft_info.json'
    version=json.loads(metadata.read_text())
    cache_index={}
    for path in (args.cache/'caches').rglob('*.jar'):
        cache_index.setdefault(path.name,[]).append(path)
    raw_client=args.cache/'caches/fabric-loom/1.21.1/minecraft-client.jar'
    if raw_client.is_file():
        cache_index.setdefault('minecraft-1.21.1-client.jar',[]).append(raw_client)
    entries=[]
    manifest=[]
    missing=[]
    def obtain(artifact, relative):
        target=artifact_path(runtime/'libraries',relative)
        candidates=cache_index.get(target.name,[])
        source=next((p for p in candidates if digest(p,'sha1')==artifact['sha1']),None)
        if source is None:
            if not args.download_missing:
                missing.append(relative)
                return
            target.parent.mkdir(parents=True,exist_ok=True)
            with urllib.request.urlopen(artifact['url'],timeout=60) as response:
                data=response.read()
            if hashlib.sha1(data).hexdigest()!=artifact['sha1'] or len(data)!=artifact['size']:
                raise ValueError(f'Download integrity failed: {relative}')
            target.write_bytes(data)
        else:
            verified_copy(source,target,artifact['sha1'])
        entries.append(str(target))
        manifest.append({'path':str(target.relative_to(runtime)),'sha256':digest(target),
                         'expected_sha1':artifact['sha1'],'source':artifact['url']})
    client=version['downloads']['client']
    obtain(client,'com/mojang/minecraft/1.21.1/minecraft-1.21.1-client.jar')
    # The cached raw client JAR uses minecraft-client.jar rather than its Maven-style target name.
    if missing and missing[0].endswith('client.jar'):
        raw=args.cache/'caches/fabric-loom/1.21.1/minecraft-client.jar'
        if raw.is_file() and digest(raw,'sha1')==client['sha1']:
            missing.pop(0)
            target=verified_copy(raw,runtime/'libraries/com/mojang/minecraft/1.21.1/minecraft-1.21.1-client.jar',client['sha1'])
            entries.append(str(target));manifest.append({'path':str(target.relative_to(runtime)),
                'sha256':digest(target),'expected_sha1':client['sha1'],'source':client['url']})
    for library in version['libraries']:
        if linux_library(library) and 'artifact' in library.get('downloads',{}):
            artifact=library['downloads']['artifact'];obtain(artifact,artifact['path'])
    server=args.server_runtime/'libraries'
    for group in ('net/fabricmc','org/ow2/asm'):
        for source in sorted((server/group).rglob('*.jar')):
            relative=source.relative_to(server)
            target=verified_copy(source,runtime/'libraries'/relative)
            entries.append(str(target));manifest.append({'path':str(target.relative_to(runtime)),
                'sha256':digest(target),'source':'previously staged official Fabric server runtime'})
    for source in filter(None,(args.drmd,args.bcc,args.api,args.gate,args.sodium)):
        target=verified_copy(source,runtime/'mods'/source.name)
        manifest.append({'path':str(target.relative_to(runtime)),'sha256':digest(target),'source':str(source)})
    if missing:
        (runtime/'missing-libraries.json').write_text(json.dumps(missing,indent=2)+'\n')
        raise ValueError(f'Missing {len(missing)} official libraries; see missing-libraries.json. Re-stage in a new directory with --download-missing if needed.')
    if args.copy_test_world:
        # This option is exclusively for the disposable gate world, never an arbitrary user save.
        world=args.server_runtime/'gate-v5-cubic-fixed'
        if not world.is_dir() or (world/'session.lock').exists() and not (world/'level.dat').is_file():
            raise ValueError('Dedicated disposable gate world not found')
        shutil.copytree(world,runtime/'saves/Cubic-Gate',ignore=shutil.ignore_patterns('session.lock'))
    cached_assets=args.cache/'caches/fabric-loom/assets'
    index=cached_assets/'indexes/1.21.1-17.json'
    if digest(index,'sha1')!=version['assetIndex']['sha1']:
        raise ValueError('Cached official asset index does not match Mojang metadata')
    assets=runtime/'assets'
    verified_copy(index,assets/'indexes/17.json',version['assetIndex']['sha1'])
    (assets/'objects').symlink_to((cached_assets/'objects').resolve(),target_is_directory=True)
    (runtime/'options.txt').write_text('onboardAccessibility:false\npauseOnLostFocus:false\nmaxFps:30\nrenderDistance:4\nsimulationDistance:5\n')
    argv=[str(args.java),'-Xmx2G','-cp',os.pathsep.join(entries),'net.fabricmc.loader.impl.launch.knot.KnotClient',
          '--gameDir',str(runtime),'--assetsDir',str(assets),'--assetIndex',version['assetIndex']['id'],
          '--version','1.21.1','--versionType','Fabric Cubic Gate','--username','CubicGate',
          '--uuid','9f3fe083b69b4dfaa4cc261026261026','--accessToken','0','--userType','legacy',
          '--width','1280','--height','720']
    if args.copy_test_world:
        argv+=['--quickPlaySingleplayer','Cubic-Gate']
    (runtime/'launch.json').write_text(json.dumps(argv,indent=2)+'\n')
    (runtime/'inputs.json').write_text(json.dumps({'status':'STAGED_NOT_RUN','artifacts':manifest,
        'experimental_native_relight_probe':False},indent=2)+'\n')
    print(json.dumps({'status':'STAGED_NOT_RUN','classpath_entries':len(entries),'artifacts':len(manifest),'runtime':str(runtime)}))


if __name__=='__main__':
    main()
