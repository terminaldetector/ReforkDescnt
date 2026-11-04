from pathlib import Path
import hashlib,importlib.util,json,subprocess,tempfile,sys,zipfile

root=Path.cwd()
spec=importlib.util.spec_from_file_location('gate',root/'scripts/checkpoint_report.py')
gate=importlib.util.module_from_spec(spec);spec.loader.exec_module(gate)
source='5d5c2b886a12b167e93f99a7e9427eb8258485fc'
jar=root/'build/libs/drmd-6dof-1.1.34-joints.1.jar'
digest=hashlib.sha256(jar.read_bytes()).hexdigest()
assert digest=='88e769462c58c21d1ffaeabea06b7cfc9deb3e334915a34b69e1d0fa77ade93a'
assert subprocess.check_output(['git','rev-parse',source],text=True).strip()==source
records=[]
for scenario in sys.argv[1:]:
    runtime=root/('build/cubic-client-final-'+scenario)
    inputs=json.loads((runtime/'inputs.json').read_text())
    assert inputs['sourceSha']==source and inputs['uncommittedDiff']=='',scenario
    assert (root/('work/body-joints/native/final-'+scenario+'.exit')).read_text().strip()=='0'
    installed=runtime/'mods'/jar.name
    assert hashlib.sha256(installed.read_bytes()).hexdigest()==digest
    with zipfile.ZipFile(installed) as z:
        assert z.testzip() is None
        assert json.loads(z.read('fabric.mod.json'))['version']=='1.1.34-joints.1'
    if 'joints' in scenario:
        name='jointcheck';verify=gate.verify_body_joints;marker='joints-ok.json';frames='joint-frames'
    elif 'body-scale' in scenario:
        name='bodyscalecheck';verify=gate.verify_body_scale;marker='body-scale-ok.json';frames='body-scale-frames'
    elif 'fragment-boarding' in scenario:
        name='fragmentboardingcheck';verify=gate.verify_fragment_boarding;marker='boarding-ok.json';frames='boarding-frames'
    elif 'ship' in scenario:
        name='shipboardingcheck';verify=gate.verify_ship_boarding;marker='ship-boarding-ok.json';frames='ship-boarding-frames'
    elif 'fracture' in scenario:
        name='fracturecheck';verify=gate.verify_body_combat;marker='body-contacts-ok.json';frames='body-contact-frames'
    else:raise ValueError(scenario)
    with tempfile.TemporaryDirectory(prefix='joint-proof-',dir=root/'work/body-joints/native') as tmp:
        mapped=Path(tmp);(mapped/'build').mkdir();(mapped/'build'/name).symlink_to(runtime,target_is_directory=True)
        proof=verify(mapped,(runtime/'inputs.json').stat().st_mtime)
        if 'body-scale' in scenario:gate.verify_pending_hull_geometry(proof)
    shots=sorted((runtime/frames).glob('*.png'));assert shots,scenario
    record=dict(scenario=scenario,sourceSha=source,jarSha256=digest,proof=proof,
                frames={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in shots})
    records.append(record)
    print(scenario,'PASS',len(shots),'fresh frames',flush=True)
(root/'work/body-joints/native/NATIVE_VERIFIED.json').write_text(json.dumps(dict(sourceSha=source,jarSha256=digest,runs=records,scope='Installed Cubic Chunks checks; full master specification remains open'),indent=2)+'\n')
