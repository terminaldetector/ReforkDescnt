#!/usr/bin/env python3
"""Distinct inventory meshes for audited duplicate icons; no item behavior or balance changes."""
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]/'src/main/resources/assets/drmd/models/item'
DISPLAY={
    'gui':{'rotation':[25,225,0],'translation':[0,0,0],'scale':[.8,.8,.8]},
    'ground':{'translation':[0,2,0],'scale':[.5,.5,.5]},
    'firstperson_righthand':{'rotation':[0,45,0],'translation':[0,2,0],'scale':[.65,.65,.65]},
    'thirdperson_righthand':{'rotation':[75,45,0],'translation':[0,2.5,0],'scale':[.55,.55,.55]},
}

def box(start,end,texture):
    return dict(from_=start,to=end,faces={side:{'uv':[0,0,16,16],'texture':'#'+texture}
               for side in ('north','south','east','west','up','down')})

def write(name,body,accent,parts):
    elements=[]
    for start,end,tex in parts:
        element=box(start,end,tex);element['from']=element.pop('from_');elements.append(element)
    data={'textures':{'body':'minecraft:block/'+body,'accent':'minecraft:block/'+accent,
                      'dark':'minecraft:block/black_concrete','particle':'minecraft:block/'+body},
          'elements':elements,'display':DISPLAY}
    (ROOT/(name+'.json')).write_text(json.dumps(data,indent=2)+'\n')

shell=[([3,5,5],[13,11,14],'body'),([5,3,8],[11,5,12],'dark')]
write('bomb_cluster','gray_concrete','yellow_concrete',shell+[
    ([x,5,1],[x+2.5,11,6],'accent') for x in (3,6.75,10.5)])
write('bomb_heavy_cluster','orange_concrete','yellow_concrete',[
    ([2,3,4],[14,13,14],'body'),([1,6,1],[5,10,6],'accent'),([6,4,1],[10,12,6],'accent'),([11,6,1],[15,10,6],'accent')])
write('bomb_cluster_virus_nether','nether_bricks','orange_concrete',shell+[
    ([2,3,2],[6,13,6],'accent'),([10,3,2],[14,13,6],'accent'),([6,6,1],[10,10,4],'dark')])
write('bomb_cluster_virus_sculk','black_concrete','cyan_concrete',shell+[
    ([2,6,1],[5,10,7],'accent'),([6,3,1],[10,6,7],'accent'),([6,10,1],[10,13,7],'accent'),([11,6,1],[14,10,7],'accent')])
write('bomb_cluster_laser','gray_concrete','red_concrete',shell+[
    ([2,4,0],[5,12,8],'accent'),([11,4,0],[14,12,8],'accent'),([6,6,1],[10,10,6],'dark')])
write('shield_orb','blue_concrete','light_blue_concrete',[
    ([5,5,5],[11,11,11],'accent'),([3,3,6],[5,13,10],'body'),([11,3,6],[13,13,10],'body'),
    ([5,3,6],[11,5,10],'body'),([5,11,6],[11,13,10],'body')])
write('energy_orb_pickup','yellow_concrete','orange_concrete',[
    ([5,3,5],[11,13,11],'body'),([3,5,5],[13,11,11],'accent'),([6,6,2],[10,10,14],'body')])
write('portal_stabilizer','purple_concrete','cyan_concrete',[
    ([3,3,5],[5,13,11],'body'),([11,3,5],[13,13,11],'body'),([5,3,5],[11,5,11],'body'),
    ([5,11,5],[11,13,11],'body'),([6,6,6],[10,10,10],'accent')])

# Preserve ancestor geometry/textures and add class-specific rails/racks to each super weapon.
for parent,child,colour,count in [
    ('concussion','flash','yellow',1),('flak','helix','lime',2),('fusion','omega','cyan',3),
    ('homing','guided','light_blue',1),('mega_missile','earthshaker','red',3),
    ('plasma','phoenix','orange',2),('smart_missile','mercury','white',2),('vulcan','gauss','purple',2),
]:
    data=json.loads((ROOT/('weapon_d6_'+parent+'.json')).read_text())
    data.setdefault('textures',{})['audit_band']='minecraft:block/'+colour+'_concrete'
    for i in range(count):
        element=box([3+i*4,10,3],[5+i*4,12,12],'audit_band');element['from']=element.pop('from_')
        data.setdefault('elements',[]).append(element)
    (ROOT/('weapon_d6_'+child+'.json')).write_text(json.dumps(data,indent=2)+'\n')

if __name__=='__main__':
    print('Wrote 16 distinct inventory meshes; validate them in the real-client inventory scene.')
