"""Generate connected multipart frames from the original single-pane directional models."""
import json
from pathlib import Path
root=Path(__file__).resolve().parents[2]/'src/main/resources/assets/drmd'
for kind in ('mirror','charged_mirror'):
    parts=[]
    for linked in ([False] if kind=='mirror' else [False,True]):
        stem=kind+('_linked' if linked else '')
        for facing in ('down','up','north','south','west','east'):
            original=json.loads((root/f'models/block/{stem}_{facing}.json').read_text())
            u,v={'north':(0,1),'south':(0,1),'west':(2,1),'east':(2,1),'up':(0,2),'down':(0,2)}[facing]
            backing,panel,*edges=original['elements']
            panel['from'][u]=panel['from'][v]=0
            panel['to'][u]=panel['to'][v]=16
            def save(name,elements):
                (root/f'models/block/{name}.json').write_text(json.dumps({'textures':original['textures'],'elements':elements},indent=2)+'\n')
            name=f'{stem}_{facing}_backing';save(name,[backing,panel])
            condition={'facing':facing}
            if kind=='charged_mirror':condition['linked']=str(linked).lower()
            parts.append({'when':condition,'apply':{'model':'drmd:block/'+name}})
            for edge in edges:
                # The short in-plane dimension identifies which frame boundary this strip occupies.
                axis=u if edge['to'][u]-edge['from'][u]==2 else v
                side='min' if edge['from'][axis]==0 else 'max'
                prop='frame_'+('u' if axis==u else 'v')+'_'+side
                other=v if axis==u else u
                edge['from'][other]=0;edge['to'][other]=16
                name=f'{stem}_{facing}_{prop}';save(name,[edge])
                parts.append({'when':dict(condition,**{prop:'true'}),'apply':{'model':'drmd:block/'+name}})
    (root/f'blockstates/{kind}.json').write_text(json.dumps({'multipart':parts},indent=2)+'\n')
