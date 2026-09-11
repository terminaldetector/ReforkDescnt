import { world, system, ItemStack, BlockPermutation, GameMode } from "@minecraft/server";

const ID={
 flame:"drmd:flame_thrower", dynamite:"drmd:dynamite_gun", lava:"drmd:lava_orb",
 flameProj:"drmd:flame_bolt", dynamiteProj:"drmd:dynamite_stick", lavaProj:"drmd:lava_orb",
 bomber:"drmd:redstone_bomber_controller", robot:"drmd:redstone_robot_controller", turret:"drmd:redstone_turret",
 shield:"drmd:redstone_shield", grenade:"drmd:redstone_grenade", grenadeProj:"drmd:redstone_grenade_projectile",
 rocket:"drmd:rocket_launcher", napalm:"drmd:napalm_cannon", plasma:"drmd:plasma_thrower", cluster:"drmd:cluster_mortar",
 slimeBomber:"drmd:slime_bomber", slimeFlyer:"drmd:slime_flyer", missile:"drmd:missile_battery", tesla:"drmd:tesla_turret", carpet:"drmd:carpet_bomber", honeyCarrier:"drmd:honey_strike_carrier",
 redstoneVampire:"drmd:redstone_vampire", pulseStorm:"drmd:pulse_storm", gravityCollapse:"drmd:gravity_collapse", chainDetonator:"drmd:chain_detonator", signalEater:"drmd:signal_eater", blackout:"drmd:redstone_blackout",
 rocketProj:"drmd:rocket_projectile", napalmProj:"drmd:napalm_projectile", plasmaProj:"drmd:plasma_projectile"
};

const EXP={
 citySettlement:'drmd:city_settlement', cityTown:'drmd:city_town', cityMono:'drmd:city_monotown', cityMedieval:'drmd:city_medieval',
 pvo:'drmd:pvo_controller', diagonal:'drmd:diagonal_cannon', guided:'drmd:guided_turret_controller', rocketBattery:'drmd:rocket_battery_controller', airEngine:'drmd:air_engine_controller',
 armedVillagers:'drmd:armed_villagers', endStalker:'drmd:enderman_stalker', endTank:'drmd:enderman_tank', endVoid:'drmd:enderman_void', endBomber:'drmd:enderman_bomber', endHunter:'drmd:enderman_hunter', drones:'drmd:kamikaze_swarm'
};
const droneEntities=new Set();
const armedResidents=new Set();
const airEngines=[];

const cooldown=new Map();
function ready(p,k,t){const key=p.id+":"+k,n=system.currentTick,u=cooldown.get(key)||0;if(n<u)return false;cooldown.set(key,n+t);return true}
function aim(p,d=1.5,y=.15){const v=p.getViewDirection(),q=p.location;return{x:q.x+v.x*d,y:q.y+v.y*d+y,z:q.z+v.z*d}}
function shoot(p,id,s){const e=p.dimension.spawnEntity(id,aim(p));const v=p.getViewDirection();const c=e.getComponent("minecraft:projectile");if(c){c.owner=p;c.shoot({x:v.x*s,y:v.y*s,z:v.z*s})}return e}
function cube(d,cx,cy,cz,x,y,z,t){for(let ix=0;ix<x;ix++)for(let iy=0;iy<y;iy++)for(let iz=0;iz<z;iz++)try{d.getBlock({x:Math.floor(cx+ix),y:Math.floor(cy+iy),z:Math.floor(cz+iz)}).setType(t)}catch{}}
function line(d,a,b,t){let n=Math.max(Math.abs(b.x-a.x),Math.abs(b.y-a.y),Math.abs(b.z-a.z));n=Math.max(1,Math.floor(n));for(let i=0;i<=n;i++){let k=i/n;try{d.getBlock({x:Math.floor(a.x+(b.x-a.x)*k),y:Math.floor(a.y+(b.y-a.y)*k),z:Math.floor(a.z+(b.z-a.z)*k)}).setType(t)}catch{}}}
function block(d,p,t){try{d.getBlock({x:Math.floor(p.x),y:Math.floor(p.y),z:Math.floor(p.z)}).setType(t)}catch{}}
function blockState(d,p,t,state){try{d.getBlock({x:Math.floor(p.x),y:Math.floor(p.y),z:Math.floor(p.z)}).setPermutation(BlockPermutation.resolve(t,state))}catch{}}
const FACE={down:0,up:1,north:2,south:3,west:4,east:5};
function dispenser(d,p,face){blockState(d,p,"minecraft:dispenser",{"facing_direction":FACE[face]})}
function observer(d,p,face){blockState(d,p,"minecraft:observer",{"facing_direction":FACE[face]})}
function piston(d,p,face,sticky=false){blockState(d,p,sticky?"minecraft:sticky_piston":"minecraft:piston",{"facing_direction":FACE[face]})}
function fillDispenser(d,p,id="minecraft:tnt",count=64){try{const c=d.getBlock({x:Math.floor(p.x),y:Math.floor(p.y),z:Math.floor(p.z)}).getComponent("minecraft:inventory")?.container;if(c)c.setItem(0,new ItemStack(id,count))}catch{}}
function buildBomber(d,c){let x=Math.floor(c.x)-4,y=Math.floor(c.y)+3,z=Math.floor(c.z)-2;cube(d,x,y,z,9,1,5,"minecraft:iron_block");cube(d,x+1,y+1,z+1,7,1,3,"minecraft:redstone_block");cube(d,x+2,y+2,z+2,5,1,1,"minecraft:observer");for(let i=0;i<4;i++){cube(d,x+i*2,y-1,z+1,1,1,1,"minecraft:dropper");cube(d,x+i*2,y-1,z+3,1,1,1,"minecraft:dropper")}for(let i=0;i<5;i++)block(d,{x:x+i,y:y+2,z:z},"minecraft:glass");return {kind:"bomber",anchor:{x,y,z},drops:[]}}
function buildRobot(d,c){let x=Math.floor(c.x)-2,y=Math.floor(c.y),z=Math.floor(c.z)-2;cube(d,x,y,z,5,1,5,"minecraft:iron_block");cube(d,x+1,y+1,z+1,3,2,3,"minecraft:observer");block(d,{x:x+2,y:y+3,z:z+2},"minecraft:redstone_lamp");for(let dx of [0,4])for(let dz of [0,4])block(d,{x:x+dx,y:y-1,z:z+dz},"minecraft:piston");return {kind:"robot",anchor:{x:x+2,y,z:z+2},shape:{x,y,z}}}
function buildTurret(d,c){let x=Math.floor(c.x)-1,y=Math.floor(c.y),z=Math.floor(c.z)-1;cube(d,x,y,z,3,1,3,"minecraft:iron_block");cube(d,x+1,y+1,z+1,1,2,1,"minecraft:redstone_block");line(d,{x:x+1,y:y+2,z:z+1},{x:x+1,y:y+2,z:z-2},"minecraft:dispenser");block(d,{x:x+1,y:y+2,z:z-1},"minecraft:observer");return {kind:"turret",anchor:{x:x+1,y:y+2,z:z+1}}}
function buildShield(d,c){let x=Math.floor(c.x)-3,y=Math.floor(c.y),z=Math.floor(c.z)-3;for(let i=0;i<7;i++){block(d,{x:x+i,y,z:z},"minecraft:glass");block(d,{x:x+i,y:y+4,z:z},"minecraft:glass");block(d,{x:x+i,y:y+2,z:z+6},"minecraft:glass")}for(let i=1;i<4;i++){block(d,{x,y:y+i,z:z},"minecraft:glass");block(d,{x:x+6,y:y+i,z:z},"minecraft:glass");block(d,{x,y:y+i,z:z+6},"minecraft:glass");block(d,{x:x+6,y:y+i,z:z+6},"minecraft:glass")}return {kind:"shield",anchor:{x:x+3,y,z:z+3}}}

// Flying-machine visual: a small slime/piston/observer prop that flies the
// bomber/flyer weapons. This is fully scripted movement, not a live redstone
// contraption - the two observers here only ever face each other (never a
// piston face), and the old "trigger" block sat diagonally off the piston,
// so neither could ever actually power a piston. Rather than debug a real
// piston circuit inside a Bedrock build, each part carries its own offset
// and placer, so a step can reliably erase the whole footprint and redraw
// it one block over - guaranteeing the visible engine always matches the
// position weapons spawn their payload from.
function engineParts(withPayload){
  const parts=[
    {o:{x:0,y:0,z:0},place:(d,p)=>block(d,p,'minecraft:slime')},
    {o:{x:1,y:0,z:0},place:(d,p)=>block(d,p,'minecraft:slime')},
    {o:{x:-1,y:0,z:0},place:(d,p)=>piston(d,p,'east',false)},
    {o:{x:2,y:0,z:0},place:(d,p)=>piston(d,p,'west',true)},
    {o:{x:0,y:1,z:0},place:(d,p)=>observer(d,p,'east')},
    {o:{x:1,y:1,z:0},place:(d,p)=>observer(d,p,'west')},
  ];
  if(withPayload){
    parts.push(
      {o:{x:0,y:-1,z:0},place:(d,p)=>block(d,p,'minecraft:iron_block')},
      {o:{x:1,y:-1,z:0},place:(d,p)=>block(d,p,'minecraft:iron_block')},
    );
  }
  return parts;
}
function drawParts(d,origin,parts){ for(const part of parts) try{part.place(d,{x:origin.x+part.o.x,y:origin.y+part.o.y,z:origin.z+part.o.z})}catch{} }
function eraseParts(d,origin,parts){ for(const part of parts) try{block(d,{x:origin.x+part.o.x,y:origin.y+part.o.y,z:origin.z+part.o.z},'minecraft:air')}catch{} }
function buildFlyingEngine(d,o,withPayload=true){
  const x=Math.floor(o.x),y=Math.floor(o.y),z=Math.floor(o.z);
  const parts=engineParts(withPayload);
  drawParts(d,{x,y,z},parts);
  return {engine:{x,y,z},anchor:{x:x,y:y-1,z:z},dir:{x:1,y:0,z:0},step:0,parts};
}
// Moves a machine's whole footprint (engine + any decorations riding along
// with it) one block in +x: erase at the old position, advance, redraw.
function advanceEngine(m){
  try{
    eraseParts(m.dimension,m.engine,m.parts);
    m.engine.x+=1; m.anchor.x+=1;
    drawParts(m.dimension,m.engine,m.parts);
  }catch{}
}
function buildSlimeBomber(d,c){
  const o={x:Math.floor(c.x)-5,y:Math.max(8,Math.floor(c.y)+1),z:Math.floor(c.z)-1};
  const parts=engineParts(true);
  for(let i=0;i<3;i++){
    parts.push({o:{x:1+i,y:0,z:-1},place:(d,p)=>block(d,p,'minecraft:iron_trapdoor')});
    parts.push({o:{x:1+i,y:0,z:2},place:(d,p)=>block(d,p,'minecraft:iron_trapdoor')});
  }
  drawParts(d,o,parts);
  return {kind:"slime_bomber",anchor:{x:o.x,y:o.y-1,z:o.z},engine:{x:o.x,y:o.y,z:o.z},dir:{x:1,y:0,z:0},step:0,dimension:d,dropEvery:8,parts};
}
function buildSlimeFlyer(d,c){
  const base={x:Math.floor(c.x)-5,y:Math.max(10,Math.floor(c.y)+2),z:Math.floor(c.z)-2};
  const parts=engineParts(false);
  for(const part of engineParts(false)) parts.push({o:{x:part.o.x,y:part.o.y,z:part.o.z+3},place:part.place});
  for(let i=0;i<4;i++) parts.push({o:{x:1+i,y:0,z:1},place:(d,p)=>block(d,p,'minecraft:honey_block')});
  drawParts(d,base,parts);
  return {kind:"slime_flyer",anchor:{x:base.x+2,y:base.y-1,z:base.z+1},engine:{x:base.x,y:base.y,z:base.z},dir:{x:1,y:0,z:0},step:0,dimension:d,dropEvery:10,parts};
}
function buildMissileBattery(d,c){
  let x=Math.floor(c.x)-2,y=Math.floor(c.y),z=Math.floor(c.z)-2;
  cube(d,x,y,z,5,2,5,"minecraft:iron_block");
  for(let i=0;i<5;i++){ block(d,{x:x+i,y:y+2,z:z+2},"minecraft:dispenser"); block(d,{x:x+i,y:y+3,z:z+2},"minecraft:observer"); }
  block(d,{x:x+2,y:y+2,z:z+1},"minecraft:redstone_block");
  return {kind:"missile",anchor:{x:x+2,y:y+2,z:z+2}};
}
function buildTesla(d,c){
  let x=Math.floor(c.x)-2,y=Math.floor(c.y),z=Math.floor(c.z)-2;
  cube(d,x,y,z,5,1,5,"minecraft:iron_block");
  for(let h=1;h<=4;h++){cube(d,x+1,y+h,z+1,3,1,3,"minecraft:glass");}
  block(d,{x:x+2,y:y+5,z:z+2},"minecraft:lightning_rod");
  for(const q of [{x:x+1,y:y+1,z:z+1},{x:x+3,y:y+1,z:z+1},{x:x+1,y:y+1,z:z+3},{x:x+3,y:y+1,z:z+3}]) block(d,q,"minecraft:observer");
  return {kind:"tesla",anchor:{x:x+2,y:y+5,z:z+2}};
}
function burst(p,id,count,speed,delay=1){ for(let i=0;i<count;i++) system.runTimeout(()=>{try{if(p.isValid)shoot(p,id,speed)}catch{}},i*delay); }
function fireNapalmPatch(d,p){ for(let i=0;i<12;i++){const a=Math.random()*Math.PI*2,r=Math.random()*4;const q={x:Math.floor(p.x+Math.cos(a)*r),y:Math.floor(p.y),z:Math.floor(p.z+Math.sin(a)*r)};try{const b=d.getBlock(q);if(b.typeId==="minecraft:air")b.setType("minecraft:fire")}catch{}} }
function firePlasmaImpact(d,p){ try{d.createExplosion(p,2.6,{breaksBlocks:false,causesFire:true})}catch{} }

// Creative convenience: place the complete DRMD arsenal into the player's inventory on spawn.
const CREATIVE_LOADOUT=[ID.flame,ID.dynamite,ID.lava,ID.bomber,ID.robot,ID.turret,ID.shield,ID.grenade,ID.rocket,ID.napalm,ID.plasma,ID.cluster,ID.slimeBomber,ID.slimeFlyer,ID.missile,ID.tesla,ID.carpet,ID.honeyCarrier,ID.redstoneVampire,ID.pulseStorm,ID.gravityCollapse,ID.chainDetonator,ID.signalEater,ID.blackout,EXP.citySettlement,EXP.cityTown,EXP.cityMono,EXP.cityMedieval,EXP.pvo,EXP.diagonal,EXP.guided,EXP.rocketBattery,EXP.airEngine,EXP.armedVillagers,EXP.endStalker,EXP.endTank,EXP.endVoid,EXP.endBomber,EXP.endHunter,EXP.drones];
function giveCreativeLoadout(p){
  try{
    if(typeof p.getGameMode === "function"){
      const gm=p.getGameMode();
      if(gm!==GameMode.Creative) return;
    }
    const inv=p.getComponent("minecraft:inventory")?.container;
    if(!inv) return;
    for(const id of CREATIVE_LOADOUT){
      let found=false;
      for(let i=0;i<inv.size;i++){const st=inv.getItem(i);if(st?.typeId===id){found=true;break}}
      if(!found) inv.addItem(new ItemStack(id,1));
    }
  }catch{}
}
world.afterEvents.playerSpawn.subscribe(ev=>{
  if(!ev.initialSpawn)return;
  system.runTimeout(()=>giveCreativeLoadout(ev.player),20);
  system.runTimeout(()=>giveCreativeLoadout(ev.player),60);
});

const machines=[];
function spawnStructure(p,id){const d=p.dimension,v=p.getViewDirection(),c={x:p.location.x+v.x*5,y:Math.max(6,p.location.y+v.y*2),z:p.location.z+v.z*5};if(id===ID.bomber)machines.push({...buildBomber(d,c),dimension:d});else if(id===ID.robot)machines.push({...buildRobot(d,c),dimension:d});else if(id===ID.turret)machines.push({...buildTurret(d,c),dimension:d});else if(id===ID.shield)machines.push({...buildShield(d,c),dimension:d});else if(id===ID.grenade)shoot(p,ID.grenadeProj,2.7);else if(id===ID.slimeBomber)machines.push({...buildSlimeBomber(d,c),dimension:d});else if(id===ID.slimeFlyer)machines.push({...buildSlimeFlyer(d,c),dimension:d});else if(id===ID.missile)machines.push({...buildMissileBattery(d,c),dimension:d});else if(id===ID.tesla)machines.push({...buildTesla(d,c),dimension:d})}
function fireTree(d,p){let r=5;for(let x=-r;x<=r;x++)for(let y=-2;y<=7;y++)for(let z=-r;z<=r;z++){if(x*x+z*z+y*y*.35>r*r)continue;try{let b=d.getBlock({x:Math.floor(p.x+x),y:Math.floor(p.y+y),z:Math.floor(p.z+z)});if(b.typeId.endsWith("_leaves"))b.setType("minecraft:air");else if(b.typeId.includes("_log")){let a=d.getBlock({x:b.location.x,y:b.location.y+1,z:b.location.z});if(a.typeId.includes("air"))a.setType("minecraft:fire")}}catch{}}}
function lavaLake(d,p){let made=[];for(let x=-4;x<=4;x++)for(let z=-4;z<=4;z++){if(x*x+z*z>16)continue;let q={x:Math.floor(p.x+x),y:Math.floor(p.y),z:Math.floor(p.z+z)};try{let b=d.getBlock(q);if(b.typeId.includes("air")||b.typeId==="minecraft:water"){b.setType("minecraft:lava");made.push(q)}}catch{}}system.runTimeout(()=>{for(const q of made)try{if(d.getBlock(q).typeId==="minecraft:lava")d.getBlock(q).setType("minecraft:air")}catch{}},80)}
function explode(d,p,power=3.8){try{d.createExplosion(p,power,{breaksBlocks:true,causesFire:true})}catch{}}

const REDSTONE_TYPES=new Set([
  "minecraft:redstone_wire","minecraft:redstone_torch","minecraft:unlit_redstone_torch","minecraft:redstone_block",
  "minecraft:repeater","minecraft:comparator","minecraft:piston","minecraft:sticky_piston",
  "minecraft:observer","minecraft:dispenser","minecraft:dropper","minecraft:target","minecraft:daylight_detector",
  "minecraft:powered_comparator","minecraft:powered_repeater"
]);
const REDSTONE_WIRE="minecraft:redstone_wire";
function blockTarget(p,max=32){
  try{return p.getBlockFromViewDirection({maxDistance:max,includeLiquidBlocks:false,includePassableBlocks:false})?.block?.location || null}catch{return null}
}
function scanRedstone(d,c,r=7){
  const out=[]; const x0=Math.floor(c.x),y0=Math.floor(c.y),z0=Math.floor(c.z);
  for(let x=-r;x<=r;x++)for(let y=-r;y<=r;y++)for(let z=-r;z<=r;z++){
    if(x*x+y*y+z*z>r*r)continue;
    try{const b=d.getBlock({x:x0+x,y:y0+y,z:z0+z}); if(b&&REDSTONE_TYPES.has(b.typeId)) out.push({x:b.location.x,y:b.location.y,z:b.location.z,type:b.typeId,perm:b.permutation});}catch{}
  }
  return out;
}
function destroyRedstone(d,c,r=7){
  const nodes=scanRedstone(d,c,r); for(const n of nodes) try{d.setBlockType(n,"minecraft:air")}catch{} return nodes.length;
}
function signalEat(d,c,r=7){
  const nodes=scanRedstone(d,c,r); let n=0;
  for(const q of nodes) try{d.setBlockType(q,"minecraft:stone");n++}catch{}
  return n;
}
function gravityCollapse(d,c,r=7){
  const nodes=scanRedstone(d,c,r); const supports=new Map();
  for(const q of nodes){
    const below={x:q.x,y:q.y-1,z:q.z};
    try{const b=d.getBlock(below); if(b&&b.typeId!=="minecraft:air"&&b.typeId!=="minecraft:bedrock") supports.set(`${below.x},${below.y},${below.z}`,below)}catch{}
  }
  for(const q of supports.values()) try{d.setBlockType(q,"minecraft:air")}catch{}
  return supports.size;
}
function chainDetonate(d,c,r=9){
  const nodes=scanRedstone(d,c,r).filter(q=>q.type===REDSTONE_WIRE).sort((a,b)=>((a.x-c.x)**2+(a.y-c.y)**2+(a.z-c.z)**2)-((b.x-c.x)**2+(b.y-c.y)**2+(b.z-c.z)**2));
  const seen=new Set(), chain=[];
  for(const q of nodes){const k=`${q.x},${q.y},${q.z}`;if(!seen.has(k)){seen.add(k);chain.push(q)}}
  try{d.createExplosion(c,3.5,{breaksBlocks:true,causesFire:true})}catch{}
  chain.slice(0,18).forEach((q,i)=>system.runTimeout(()=>{try{d.createExplosion({x:q.x+.5,y:q.y+.2,z:q.z+.5},Math.max(1.2,3.2-i*.1),{breaksBlocks:true,causesFire:i<5})}catch{}},4+i*3));
  return chain.length;
}
function pulseStorm(d,c,r=7){
  const nodes=scanRedstone(d,c,r); const candidates=[];
  for(const q of nodes){
    for(const o of [{x:q.x+1,y:q.y,z:q.z},{x:q.x-1,y:q.y,z:q.z},{x:q.x,y:q.y+1,z:q.z},{x:q.x,y:q.y-1,z:q.z},{x:q.x,y:q.y,z:q.z+1},{x:q.x,y:q.y,z:q.z-1}]){
      try{const b=d.getBlock(o);if(b&&b.typeId==="minecraft:air"){candidates.push(o);break}}catch{}
    }
  }
  for(let i=0;i<24;i++) system.runTimeout(()=>{
    if(!candidates.length)return;
    const q=candidates[Math.floor(Math.random()*candidates.length)];
    try{d.setBlockType(q,"minecraft:redstone_block");system.runTimeout(()=>{try{if(d.getBlock(q)?.typeId==="minecraft:redstone_block")d.setBlockType(q,"minecraft:air")}catch{}},2)}catch{}
  },i*2);
}
function blackout(d,c,r=7){
  const nodes=scanRedstone(d,c,r).filter(q=>["minecraft:redstone_wire","minecraft:redstone_torch","minecraft:unlit_redstone_torch","minecraft:repeater","minecraft:comparator","minecraft:redstone_block"].includes(q.type));
  const saved=nodes.map(q=>({x:q.x,y:q.y,z:q.z,perm:q.perm}));
  for(const q of saved)try{d.setBlockType(q,"minecraft:air")}catch{}
  system.runTimeout(()=>{for(const q of saved)try{d.setBlockPermutation(q,q.perm)}catch{}},100);
}
function chaosAt(p,id){
  const t=blockTarget(p,32)||{x:p.location.x+Math.floor(p.getViewDirection().x*6),y:Math.floor(p.location.y),z:p.location.z+Math.floor(p.getViewDirection().z*6)};
  if(id===ID.redstoneVampire){if(ready(p,"rv",10))destroyRedstone(p.dimension,t,7);return true}
  if(id===ID.pulseStorm){if(ready(p,"ps",12))pulseStorm(p.dimension,t,7);return true}
  if(id===ID.gravityCollapse){if(ready(p,"gc",14))gravityCollapse(p.dimension,t,7);return true}
  if(id===ID.chainDetonator){if(ready(p,"cd",20))chainDetonate(p.dimension,t,9);return true}
  if(id===ID.signalEater){if(ready(p,"se",12))signalEat(p.dimension,t,7);return true}
  if(id===ID.blackout){if(ready(p,"bo",20))blackout(p.dimension,t,7);return true}
  return false;
}


function cityBlock(d,q,t){try{const b=d.getBlock({x:Math.floor(q.x),y:Math.floor(q.y),z:Math.floor(q.z)});if(b&&b.typeId!=='minecraft:bedrock')b.setType(t)}catch{}}
function rect(d,x0,y0,z0,w,h,l,t){for(let x=0;x<w;x++)for(let y=0;y<h;y++)for(let z=0;z<l;z++)cityBlock(d,{x:x0+x,y:y0+y,z:z0+z},t)}
function house(d,x,y,z,w=7,l=7,h=5,wall='minecraft:oak_planks',roof='minecraft:dark_oak_planks'){
  rect(d,x,y,z,w,1,l,wall); rect(d,x,y+1,z,1,h,l,wall); rect(d,x+w-1,y+1,z,1,h,l,wall); rect(d,x,y+1,z,w,h,1,wall); rect(d,x,y+1,z+l-1,w,h,1,wall);
  for(let xx=x+1;xx<x+w-1;xx++)for(let zz=z+1;zz<z+l-1;zz++)if((xx+zz)%3===0)cityBlock(d,{x:xx,y:y+h,z:zz},roof);
  cityBlock(d,{x:x+Math.floor(w/2),y:y+1,z:z},'minecraft:oak_door'); cityBlock(d,{x:x+1,y:y+2,z:z},'minecraft:glass'); cityBlock(d,{x:x+w-2,y:y+2,z:z+l-1},'minecraft:glass');
}
function roadGrid(d,c,size,blockType='minecraft:stone'){
  const x0=Math.floor(c.x-size/2),z0=Math.floor(c.z-size/2),y=Math.floor(c.y);
  for(let x=0;x<size;x++)for(let z=0;z<size;z++)if(x%8<2||z%8<2)cityBlock(d,{x:x0+x,y,z:z0+z},blockType);
}
function buildSettlement(d,c){const y=Math.floor(c.y),x=Math.floor(c.x),z=Math.floor(c.z);const size=31;roadGrid(d,c,size,'minecraft:gravel');for(let ix=0;ix<3;ix++)for(let iz=0;iz<3;iz++)house(d,x-14+ix*10,y,z-14+iz*10,7,7,4);for(let i=0;i<8;i++)cityBlock(d,{x:x-3+i,y:y+1,z:z-2},'minecraft:hay_block');}
function buildTown(d,c){const y=Math.floor(c.y),x=Math.floor(c.x),z=Math.floor(c.z),size=47;roadGrid(d,c,size,'minecraft:stone');for(let ix=0;ix<4;ix++)for(let iz=0;iz<4;iz++)house(d,x-21+ix*12,y,z-21+iz*12,9,9,6,ix===0?'minecraft:birch_planks':'minecraft:oak_planks',ix===1?'minecraft:spruce_planks':'minecraft:dark_oak_planks');for(let i=-15;i<=15;i+=6)cityBlock(d,{x:x+i,y:y+1,z:z},'minecraft:lantern');}
function buildMono(d,c){const y=Math.floor(c.y),x=Math.floor(c.x),z=Math.floor(c.z),size=55;roadGrid(d,c,size,'minecraft:polished_andesite');for(let bx=-18;bx<=18;bx+=12)for(let bz=-18;bz<=18;bz+=12){const h=10+((Math.abs(bx)+Math.abs(bz))%17);rect(d,x+bx,y,z+bz,9,h,9,'minecraft:stone');for(let yy=1;yy<h-1;yy+=2)for(let xx=1;xx<8;xx+=2)cityBlock(d,{x:x+bx+xx,y:y+yy,z:z+bz},'minecraft:glass');}rect(d,x-3,y,z-23,6,16,6,'minecraft:bricks');for(let i=0;i<3;i++)rect(d,x-18+i*18,y,z+20,5,6,5,'minecraft:iron_block');}
function buildMedieval(d,c){const y=Math.floor(c.y),x=Math.floor(c.x),z=Math.floor(c.z),size=51;rect(d,x-25,y,z-25,size,2,size,'minecraft:cobblestone');for(let i=-25;i<=25;i++){cityBlock(d,{x:x-25,y:y+2,z:z+i},'minecraft:stone_bricks');cityBlock(d,{x:x+25,y:y+2,z:z+i},'minecraft:stone_bricks');cityBlock(d,{x:x+i,y:y+2,z:z-25},'minecraft:stone_bricks');cityBlock(d,{x:x+i,y:y+2,z:z+25},'minecraft:stone_bricks');}for(const [dx,dz] of [[-22,-22],[-22,22],[22,-22],[22,22]]){rect(d,x+dx-2,y+3,z+dz-2,5,9,5,'minecraft:stone_bricks');}for(let ix=-14;ix<=14;ix+=14)for(let iz=-14;iz<=14;iz+=14)house(d,x+ix-4,y+2,z+iz-4,8,8,7,'minecraft:stone_bricks','minecraft:oak_planks');}
function buildCity(d,c,kind){if(kind==='settlement')buildSettlement(d,c);else if(kind==='town')buildTown(d,c);else if(kind==='mono')buildMono(d,c);else buildMedieval(d,c);}
function spawnArmedVillagers(d,c,count=8){for(let i=0;i<count;i++){const q={x:c.x+(Math.random()*10-5),y:c.y,z:c.z+(Math.random()*10-5)};try{const e=d.spawnEntity('minecraft:villager_v2',q);e.nameTag='§6Вооружённый житель';e.addTag('drmd_armed_villager');e.addEffect('minecraft:resistance',999999,{amplifier:0,showParticles:false});armedResidents.add(e)}catch{}}}
function spawnEndVariant(d,c,variant,count=2){for(let i=0;i<count;i++){const q={x:c.x+(Math.random()*8-4),y:c.y+1,z:c.z+(Math.random()*8-4)};try{const e=d.spawnEntity('minecraft:enderman',q);e.addTag('drmd_end_variant');e.addTag('drmd_end_'+variant);e.nameTag='§5ENDERMAN / '+variant.toUpperCase();if(variant==='stalker'){e.addEffect('minecraft:speed',999999,{amplifier:1,showParticles:false});e.addEffect('minecraft:invisibility',999999,{amplifier:0,showParticles:false})}if(variant==='tank'){e.addEffect('minecraft:resistance',999999,{amplifier:2,showParticles:false});e.addEffect('minecraft:strength',999999,{amplifier:1,showParticles:false})}if(variant==='void'){e.addEffect('minecraft:regeneration',999999,{amplifier:1,showParticles:false});e.addEffect('minecraft:slow_falling',999999,{amplifier:0,showParticles:false})}if(variant==='bomber'){e.addEffect('minecraft:jump_boost',999999,{amplifier:2,showParticles:false})}if(variant==='hunter'){e.addEffect('minecraft:speed',999999,{amplifier:3,showParticles:false});e.addEffect('minecraft:strength',999999,{amplifier:0,showParticles:false})} }catch{}}}
function boidDroneStep(e){if(!e?.isValid)return;const loc=e.location;let target=null,td=99999;try{for(const p of world.getAllPlayers()){const dx=p.location.x-loc.x,dy=p.location.y-loc.y,dz=p.location.z-loc.z,d=dx*dx+dy*dy+dz*dz;if(d<td){td=d;target=p}}}catch{}if(!target)return;const neigh=[...droneEntities].filter(n=>{try{return n!==e&&n.isValid&&Math.hypot(n.location.x-loc.x,n.location.y-loc.y,n.location.z-loc.z)<10}catch{return false}});let ax=0,ay=0,az=0,sx=0,sy=0,sz=0,cx=0,cy=0,cz=0;for(const n of neigh){try{const v=n.getVelocity?.()||{x:0,y:0,z:0};ax+=v.x;ay+=v.y;az+=v.z;cx+=n.location.x;cy+=n.location.y;cz+=n.location.z;const dx=loc.x-n.location.x,dy=loc.y-n.location.y,dz=loc.z-n.location.z,ds=dx*dx+dy*dy+dz*dz+0.1;sx+=dx/ds;sy+=dy/ds;sz+=dz/ds}catch{}}const k=neigh.length||1;if(neigh.length){ax/=k;ay/=k;az/=k;cx=cx/k;cy=cy/k;cz=cz/k;}const tx=target.location.x-loc.x,ty=target.location.y+1.2-loc.y,tz=target.location.z-loc.z,tl=Math.hypot(tx,ty,tz)||1;let vx=ax*.18+sx*1.6+(cx?((cx-loc.x)*.05):0)+tx/tl*1.1;let vy=ay*.18+sy*1.6+(cy?((cy-loc.y)*.05):0)+ty/tl*1.1;let vz=az*.18+sz*1.6+(cz?((cz-loc.z)*.05):0)+tz/tl*1.1;const vl=Math.hypot(vx,vy,vz)||1;e.applyImpulse({x:vx/vl*.25,y:vy/vl*.25,z:vz/vl*.25});if(td<9){try{e.dimension.createExplosion(loc,2.2,{breaksBlocks:true,causesFire:true});e.remove()}catch{}}}
function spawnDroneSwarm(d,c,count=24){for(let i=0;i<count;i++){const a=i/count*Math.PI*2,r=4+Math.random()*5,q={x:c.x+Math.cos(a)*r,y:c.y+2+Math.random()*6,z:c.z+Math.sin(a)*r};try{const e=d.spawnEntity('minecraft:vex',q);e.nameTag='§cKAMIKAZE DRONE';e.addTag('drmd_kamikaze');e.addEffect('minecraft:resistance',999999,{amplifier:1,showParticles:false});droneEntities.add(e)}catch{}}}
function pvoTarget(d,c,range=64){let best=null,bd=range*range;try{for(const e of d.getEntities({location:c,maxDistance:range})){if(e.typeId==='minecraft:player')continue;if(e.typeId==='minecraft:vex' || e.typeId==='minecraft:enderman' || e.typeId.startsWith('drmd:')){const dx=e.location.x-c.x,dy=e.location.y-c.y,dz=e.location.z-c.z,dd=dx*dx+dy*dy+dz*dz;if(dd<bd){bd=dd;best=e}}}}catch{}return best}
function firePvo(d,c,mode){const t=pvoTarget(d,c);if(!t)return;const v={x:t.location.x-c.x,y:t.location.y-c.y,z:t.location.z-c.z},l=Math.hypot(v.x,v.y,v.z)||1;try{const e=d.spawnEntity(mode==='rocket'?'drmd:rocket_projectile':'drmd:plasma_projectile',c);e.getComponent('minecraft:projectile')?.shoot({x:v.x/l*(mode==='rocket'?4.8:3.8),y:v.y/l*(mode==='rocket'?4.8:3.8),z:v.z/l*(mode==='rocket'?4.8:3.8)})}catch{}}
function activateExpansionItem(p,id){const d=p.dimension;const c=blockTarget(p,32)||{x:p.location.x+p.getViewDirection().x*8,y:p.location.y,z:p.location.z+p.getViewDirection().z*8};if(id===EXP.citySettlement){if(ready(p,'cityS',40))buildCity(d,c,'settlement');return true}if(id===EXP.cityTown){if(ready(p,'cityT',50))buildCity(d,c,'town');return true}if(id===EXP.cityMono){if(ready(p,'cityM',60))buildCity(d,c,'mono');return true}if(id===EXP.cityMedieval){if(ready(p,'cityMed',60))buildCity(d,c,'medieval');return true}if(id===EXP.armedVillagers){if(ready(p,'vill',30))spawnArmedVillagers(d,c,10);return true}if(id===EXP.endStalker){if(ready(p,'es',20))spawnEndVariant(d,c,'stalker',3);return true}if(id===EXP.endTank){if(ready(p,'et',20))spawnEndVariant(d,c,'tank',2);return true}if(id===EXP.endVoid){if(ready(p,'ev',20))spawnEndVariant(d,c,'void',3);return true}if(id===EXP.endBomber){if(ready(p,'eb',20))spawnEndVariant(d,c,'bomber',3);return true}if(id===EXP.endHunter){if(ready(p,'eh',20))spawnEndVariant(d,c,'hunter',3);return true}if(id===EXP.drones){if(ready(p,'drones',30))spawnDroneSwarm(d,c,28);return true}if(id===EXP.pvo||id===EXP.diagonal||id===EXP.guided||id===EXP.rocketBattery||id===EXP.airEngine){if(ready(p,id,25)){const b= id===EXP.pvo?'drmd:guided_turret':id===EXP.diagonal?'drmd:diagonal_cannon':id===EXP.guided?'drmd:guided_turret':id===EXP.rocketBattery?'drmd:rocket_rack':'drmd:air_engine_core';const q={x:Math.floor(c.x),y:Math.floor(c.y),z:Math.floor(c.z)};block(d,q,b); if(id===EXP.airEngine){buildFlyingEngine(d,{x:q.x+1,y:q.y+2,z:q.z},false);airEngines.push({dimension:d,engine:{x:q.x+1,y:q.y+2,z:q.z}})} else {for(let dx=-1;dx<=1;dx++)for(let dz=-1;dz<=1;dz++)block(d,{x:q.x+dx,y:q.y-1,z:q.z+dz},'minecraft:iron_block');}}return true}return false}
function activateBlock(e){const b=e.block;if(!b)return;const p=e.player;if(!p)return;const id=b.typeId;if(id==='drmd:diagonal_cannon'){if(ready(p,'dbc',10))firePvo(b.dimension,{x:b.location.x+.5,y:b.location.y+1,z:b.location.z+.5},'plasma')}else if(id==='drmd:guided_turret'){if(ready(p,'gbc',8))firePvo(b.dimension,{x:b.location.x+.5,y:b.location.y+1,z:b.location.z+.5},'plasma')}else if(id==='drmd:rocket_rack'){if(ready(p,'rbc',12))for(let i=0;i<3;i++)system.runTimeout(()=>firePvo(b.dimension,{x:b.location.x+.5,y:b.location.y+1,z:b.location.z+.5},'rocket'),i*3)}else if(id==='drmd:air_engine_core'){if(ready(p,'aec',20)){buildFlyingEngine(b.dimension,{x:b.location.x+1,y:b.location.y+2,z:b.location.z},false);airEngines.push({dimension:b.dimension,engine:{x:b.location.x+1,y:b.location.y+2,z:b.location.z}})}}else if(id==='drmd:shield_emitter'){try{for(const ent of b.dimension.getEntities({location:b.location,maxDistance:8})){if(ent.typeId==='minecraft:player')ent.addEffect('minecraft:resistance',120,{amplifier:2,showParticles:false})}}catch{}}}

function activateItem(p,id){
  try{
    if(!p||!id)return false;
    if(activateExpansionItem(p,id)) return true;
    if(chaosAt(p,id)) return true;
    if(id===ID.flame){
      if(!ready(p,"f",2))return true;
      for(let i=0;i<3;i++)system.runTimeout(()=>{try{if(p.isValid)shoot(p,ID.flameProj,4.4)}catch{}},i);
      return true;
    }
    if(id===ID.dynamite){
      if(ready(p,"d",2))shoot(p,ID.dynamiteProj,3.8);
      return true;
    }
    if(id===ID.lava){
      if(ready(p,"l",8))shoot(p,ID.lavaProj,4.5);
      return true;
    }
    if(id===ID.carpet){ if(ready(p,"carpet",10)) spawnStructure(p,ID.slimeBomber); return true; }
    if(id===ID.honeyCarrier){ if(ready(p,"honeycarrier",10)) spawnStructure(p,ID.slimeFlyer); return true; }
    if(id===ID.rocket){ if(ready(p,"rocket",3)) burst(p,ID.rocketProj,2,5.8,2); return true; }
    if(id===ID.napalm){ if(ready(p,"napalm",5)){ burst(p,ID.napalmProj,5,4.0,2); system.runTimeout(()=>fireNapalmPatch(p.dimension,aim(p,14,0)),12); } return true; }
    if(id===ID.plasma){ if(ready(p,"plasma",5)) burst(p,ID.plasmaProj,2,6.5,2); return true; }
    if(id===ID.cluster){ if(ready(p,"cluster",10)){ burst(p,ID.rocketProj,7,3.6,4); } return true; }
    if([ID.bomber,ID.robot,ID.turret,ID.shield,ID.grenade,ID.slimeBomber,ID.slimeFlyer,ID.missile,ID.tesla,ID.carpet,ID.honeyCarrier].includes(id)){
      if(ready(p,id,10))spawnStructure(p,id);
      return true;
    }
  }catch{}
  return false;
}

// Robust right-click handling for custom items. Custom item onUse is the primary path.
try{
  system.beforeEvents.startup.subscribe(({itemComponentRegistry})=>{
    itemComponentRegistry.registerCustomComponent("drmd:weapon_use",{
      onUse(e){ try{ activateItem(e.source,e.itemStack?.typeId); }catch{} },
      onUseOn(e){ try{ activateItem(e.source,e.itemStack?.typeId); }catch{} }
    });
  });
}catch{}


try{
  system.beforeEvents.startup.subscribe(({blockComponentRegistry})=>{
    blockComponentRegistry.registerCustomComponent('drmd:block_action',{onPlayerInteract(e){try{activateBlock(e)}catch{}}});
  });
}catch{}

// Secondary fallback: successful item-use event. Cooldowns make it safe if both paths fire.
try{
  world.afterEvents.itemUse.subscribe(ev=>{
    try{ activateItem(ev.source,ev.itemStack?.typeId); }catch{}
  });
}catch{}

world.afterEvents.projectileHitBlock.subscribe(ev=>{const id=ev.projectile?.typeId;if(id===ID.dynamiteProj){explode(ev.dimension,ev.location);ev.projectile.remove()}else if(id===ID.flameProj){fireTree(ev.dimension,ev.location);ev.projectile.remove()}else if(id===ID.lavaProj){lavaLake(ev.dimension,ev.location);ev.projectile.remove()}else if(id===ID.grenadeProj){explode(ev.dimension,ev.location,3);ev.projectile.remove()}else if(id===ID.rocketProj){explode(ev.dimension,ev.location,5.5);ev.projectile.remove()}else if(id===ID.napalmProj){explode(ev.dimension,ev.location,1.8);fireNapalmPatch(ev.dimension,ev.location);ev.projectile.remove()}else if(id===ID.plasmaProj){firePlasmaImpact(ev.dimension,ev.location);ev.projectile.remove()}});
world.afterEvents.projectileHitEntity.subscribe(ev=>{const id=ev.projectile?.typeId;if(id===ID.dynamiteProj){explode(ev.dimension,ev.location);ev.projectile.remove()}else if(id===ID.flameProj){try{const h=ev.getEntityHit()?.entity;if(h)h.setOnFire(8,true)}catch{}ev.projectile.remove()}else if(id===ID.lavaProj){lavaLake(ev.dimension,ev.location);ev.projectile.remove()}else if(id===ID.grenadeProj){explode(ev.dimension,ev.location,3);ev.projectile.remove()}else if(id===ID.rocketProj){explode(ev.dimension,ev.location,5.5);ev.projectile.remove()}else if(id===ID.napalmProj){explode(ev.dimension,ev.location,1.8);fireNapalmPatch(ev.dimension,ev.location);ev.projectile.remove()}else if(id===ID.plasmaProj){firePlasmaImpact(ev.dimension,ev.location);ev.projectile.remove()}});
// Construction machines operate as block structures. No redstone machine entities are spawned.
// Cosmetic flash for the stationary air-engine prop (it isn't meant to move,
// unlike the slime bomber/flyer - see advanceEngine above).
function pulseEngine(m){
  try{
    const e=m.engine;
    const q={x:e.x-1,y:e.y+1,z:e.z+1};
    block(m.dimension,q,'minecraft:redstone_block');
    system.runTimeout(()=>block(m.dimension,q,'minecraft:air'),1);
  }catch{}
}


system.runInterval(()=>{
  for(const e of [...droneEntities]){try{if(!e.isValid){droneEntities.delete(e);continue}boidDroneStep(e)}catch{droneEntities.delete(e)}}
  for(const e of [...armedResidents]){try{if(!e.isValid){armedResidents.delete(e);continue}const t=world.getAllPlayers().sort((a,b)=>a.location.x*a.location.x+a.location.z*a.location.z-b.location.x*b.location.x-b.location.z*b.location.z)[0];if(t){const dx=t.location.x-e.location.x,dy=t.location.y-e.location.y,dz=t.location.z-e.location.z,dd=Math.hypot(dx,dy,dz);if(dd<32&&system.currentTick%30===0){const l=dd||1;const r=e.dimension.spawnEntity('drmd:rocket_projectile',{x:e.location.x,y:e.location.y+1,z:e.location.z});r.getComponent('minecraft:projectile')?.shoot({x:dx/l*3.2,y:dy/l*3.2,z:dz/l*3.2})}}}catch{armedResidents.delete(e)}}
  for(const dim of [world.getDimension('overworld'),world.getDimension('nether'),world.getDimension('the_end')]){
    try{for(const e of dim.getEntities({tags:['drmd_end_bomber'],maxDistance:64})){const pl=world.getAllPlayers()[0];if(pl&&Math.hypot(pl.location.x-e.location.x,pl.location.y-e.location.y,pl.location.z-e.location.z)<6&&system.currentTick%20===0)dim.createExplosion(e.location,2.5,{breaksBlocks:true,causesFire:true})}}catch{}
  }
},4);

system.runInterval(()=>{for(const m of machines){try{
  if(m.kind==="bomber"&&system.currentTick%8===0){const p=m.anchor;const q={x:p.x+Math.floor(Math.random()*7)-3,y:p.y-1,z:p.z+Math.floor(Math.random()*5)};const t=m.dimension.spawnEntity("minecraft:tnt",q);t.applyImpulse({x:(Math.random()-.5)*.15,y:-.4,z:(Math.random()-.5)*.15})}
  else if(m.kind==="slime_bomber"&&system.currentTick%4===0){
    advanceEngine(m);
    m.step=(m.step||0)+1;
    if(m.step%2===0){const p=m.anchor;for(let i=0;i<2;i++){const q={x:p.x+i,y:p.y-1,z:p.z+(i%2)};const t=m.dimension.spawnEntity("minecraft:tnt",q);t.applyImpulse({x:.05,y:-.65,z:(Math.random()-.5)*.12})}}
  }
  else if(m.kind==="slime_flyer"&&system.currentTick%4===0){
    advanceEngine(m);
    m.step=(m.step||0)+1;
    if(m.step%3===0){const p=m.anchor;for(const dz of [-1,1]){const q={x:p.x,y:p.y-1,z:p.z+dz};const r=m.dimension.spawnEntity(ID.rocketProj,q);r.getComponent("minecraft:projectile")?.shoot({x:.3,y:-.7,z:dz*.08})}}
  }
  else if(m.kind==="missile"&&system.currentTick%12===0){const e=nearest(m.dimension,m.anchor,48);if(e){for(let i=0;i<2;i++){const v={x:e.location.x-m.anchor.x,y:e.location.y-m.anchor.y,z:e.location.z-m.anchor.z},l=Math.hypot(v.x,v.y,v.z)||1,g=m.dimension.spawnEntity(ID.rocketProj,{x:m.anchor.x,y:m.anchor.y,z:m.anchor.z});g.getComponent("minecraft:projectile")?.shoot({x:v.x/l*3.3,y:v.y/l*3.3,z:v.z/l*3.3})}}}
  else if((m.kind==="turret"||m.kind==="tesla")&&system.currentTick%(m.kind==="tesla"?10:15)===0){const e=nearest(m.dimension,m.anchor,32);if(e){const v={x:e.location.x-m.anchor.x,y:e.location.y-m.anchor.y,z:e.location.z-m.anchor.z},l=Math.hypot(v.x,v.y,v.z)||1,g=m.dimension.spawnEntity(m.kind==="tesla"?ID.plasmaProj:ID.grenadeProj,m.anchor);g.getComponent("minecraft:projectile")?.shoot({x:v.x/l*2.4,y:v.y/l*2.4,z:v.z/l*2.4})}}
  else if(m.kind==="shield"&&system.currentTick%20===0){/* physical shield */}
}catch{}}},2);

system.runInterval(()=>{
  for(const a of [...airEngines]){try{if(!a.dimension.getBlock(a.engine))continue;pulseEngine(a)}catch{}}
  if(system.currentTick%20===0){for(const d of [...droneEntities]){try{if(!d.isValid)droneEntities.delete(d)}catch{droneEntities.delete(d)}}}
},10);

function nearest(d,o,max){
  let best=null,bd=max*max;
  for(const e of d.getEntities({location:o,maxDistance:max})){
    if(!e.isValid) continue;
    if(e.typeId==='minecraft:player') continue;
    if(e.typeId.startsWith('minecraft:') && e.typeId!=='minecraft:tnt') continue;
    const x=e.location.x-o.x,y=e.location.y-o.y,z=e.location.z-o.z,dd=x*x+y*y+z*z;
    if(dd<bd){bd=dd;best=e}
  }
  return best;
}
