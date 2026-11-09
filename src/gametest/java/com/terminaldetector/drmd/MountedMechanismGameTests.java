package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import com.terminaldetector.drmd.world.contraption.*;
import com.terminaldetector.drmd.world.contraption.mechanism.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.property.Properties;
import net.minecraft.test.*;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import java.util.*;
import java.util.function.Function;

/** Existing physical barrels, real lever material and the production post-contact schedule. */
public final class MountedMechanismGameTests implements FabricGameTest {
    private static final BlockPos GUN=BlockPos.ORIGIN;
    private Vec3 pure(Vec3d v){return new Vec3(v.x,v.y,v.z);}
    private BlockState gun(boolean rocket,Direction facing){return (rocket?ModWorldBlocks.SHIP_ROCKET:ModWorldBlocks.SHIP_LASER).getDefaultState().with(Properties.FACING,facing);}
    private BlockState lever(Direction support,boolean on){
        return Blocks.LEVER.getDefaultState().with(Properties.POWERED,on)
            .with(Properties.BLOCK_FACE,support==Direction.UP?BlockFace.CEILING:support==Direction.DOWN?BlockFace.FLOOR:BlockFace.WALL)
            .with(Properties.HORIZONTAL_FACING,support.getAxis().isHorizontal()?support.getOpposite():Direction.NORTH);
    }
    private Map<BlockPos,BlockState> mount(boolean rocket,Direction facing,boolean on){return new LinkedHashMap<>(Map.of(GUN,gun(rocket,facing),GUN.offset(facing.getOpposite()),lever(facing,on)));}
    private Vec3d at(TestContext c){var p=c.getAbsolutePos(new BlockPos(8,220,8));for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)c.getWorld().getChunk((p.getX()>>4)+x,(p.getZ()>>4)+z);return Vec3d.of(p);}
    private BlockBodyEntity body(TestContext c,Map<BlockPos,BlockState> cells,Vec3d at,Quat q){
        var list=cells.entrySet().stream().map(e->new BlockBodyEntity.Cell(e.getKey(),e.getValue())).toList();
        var b=BlockBodyEntity.createLocal(c.getWorld(),list,at,q,Vec3d.ZERO,Vec3d.ZERO);b.setPhysicsGravity(false);return b;
    }
    private Map<BlockPos,BlockState> heavy(Map<BlockPos,BlockState> cells){
        var result=new LinkedHashMap<>(cells);for(int x=-4;x<=5;x++)for(int z=-4;z<=5;z++)result.put(new BlockPos(x,-1,z),Blocks.IRON_BLOCK.getDefaultState());return result;
    }
    private List<ProjectileEntity> shots(TestContext c,BlockBodyEntity b){return c.getWorld().getEntitiesByClass(ProjectileEntity.class,b.getBoundingBox().expand(256),p->p.getOwnerEntity()==b);}
    private void clean(TestContext c,BlockBodyEntity b){for(var shot:shots(c,b))shot.discard();BodyJointRegistry.get(c.getWorld()).removeBody(b.getUuid());b.discard();}
    private ServerPlayerEntity player(TestContext c,BlockBodyEntity b){var p=c.createMockCreativeServerPlayerInWorld();DescentPlayerData.get(p).setEnabled(false);p.setPosition(b.localToWorld(new Vec3d(.5,-1,-2)));return p;}
    private void admitted(TestContext c,Function<AiFixtureChunks,Runnable> setup){
        var base=c.getAbsolutePos(new BlockPos(8,220,8));var lease=new AiFixtureChunks(c.getWorld(),base,3);boolean[] started={false};int[] ticks={0};Runnable[] scene={null};
        // Register once: adding another runAtEveryTick from inside this callback mutates
        // GameTestState's active Fastutil task map while it is being iterated.
        c.runAtEveryTick(()->{
            if(!started[0]){
                if(!lease.entityTicking(base.add(-32,0,-32),base.add(32,0,32))){if(++ticks[0]<80)return;started[0]=true;lease.close();c.assertTrue(false,"mounted fixture needs actual entity-ticking columns");return;}
                started[0]=true;try{scene[0]=setup.apply(lease);}catch(Throwable failure){lease.close();throw failure;}
            }
            if(scene[0]!=null)scene[0].run();
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="mounted-direction")
    public void allSixLocalAxesInheritMaterialVelocityAndApplyPointRecoilUnderBank(TestContext c){
        var q=ImmPtlQuaternions.rotationByRadians(new Vec3(1,2,3).normalized(),1.2);var origin=at(c);
        for(var face:Direction.values()){
            var b=body(c,mount(false,face,false),origin,q);
            try{
                b.physics().withLinearVelocity(new Vec3(2,-1,3)).withAngularVelocity(new Vec3(.3,-.2,.4));
                var muzzle=MountedWeaponEmitter.muzzle(b,GUN);var direction=q.rotate(new Vec3(face.getOffsetX(),face.getOffsetY(),face.getOffsetZ()));
                var expected=b.surfaceVelocityAt(muzzle).multiply(.05).add(direction.x()*2.5,direction.y()*2.5,direction.z()*2.5);
                var velocity=b.physics().linearVelocity();var angular=b.physics().angularMomentum();var offset=pure(muzzle).minus(b.massPosition());var impulse=direction.scaled(-24);
                var shot=MountedWeaponEmitter.emit(b,b,GUN);c.assertTrue(shot!=null&&shot.getOwnerEntity()==b,"real physical gun owns its projectile: "+face);
                c.assertTrue(shot.getPos().distanceTo(muzzle)<1e-8&&shot.getVelocity().distanceTo(expected)<1e-8,"actual muzzle and XYZ point inheritance: "+face);
                c.assertTrue(b.physics().linearVelocity().minus(velocity).scaled(b.physicsMass()).minus(impulse).length()<1e-7,"linear recoil belongs to the gun body");
                c.assertTrue(b.physics().angularMomentum().minus(angular).minus(offset.cross(impulse)).length()<1e-7,"off-centre recoil applies the true point torque");
            }finally{clean(c,b);}
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="mounted-cover")
    public void LocalArmorTerrainAndOtherBodyEachObstructTheRealBarrel(TestContext c){
        var origin=at(c);var b=body(c,mount(false,Direction.SOUTH,false),origin,Quat.IDENTITY);BlockBodyEntity other=null;
        var front=new BlockPos(0,0,1);var terrain=BlockPos.ofFloored(origin).add(front);
        try{
            b.setLocalBlock(front,Blocks.IRON_BLOCK.getDefaultState());c.assertTrue(MountedWeaponEmitter.emit(b,b,GUN)==null,"local armor blocks muzzle corridor");b.setLocalBlock(front,Blocks.AIR.getDefaultState());
            c.getWorld().setBlockState(terrain,Blocks.IRON_BLOCK.getDefaultState(),2);c.assertTrue(MountedWeaponEmitter.emit(b,b,GUN)==null,"world wall blocks muzzle corridor");c.getWorld().setBlockState(terrain,Blocks.AIR.getDefaultState(),2);
            other=body(c,Map.of(GUN,Blocks.IRON_BLOCK.getDefaultState()),origin.add(0,0,1),Quat.IDENTITY);c.assertTrue(MountedWeaponEmitter.emit(b,b,GUN)==null,"unrelated physical body blocks muzzle corridor");other.discard();
            c.assertTrue(MountedWeaponEmitter.emit(b,b,GUN)!=null,"removing obstruction restores a real shot");
        }finally{if(other!=null)other.discard();c.getWorld().setBlockState(terrain,Blocks.AIR.getDefaultState(),2);clean(c,b);}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="mounted-legacy")
    public void legacyWeaponStatesKeepPositiveZAndLeversUseSmallSelectableShapes(TestContext c){
        var tag=BodyShapeCodec.write(List.of(new BlockBodyEntity.Cell(GUN,ModWorldBlocks.SHIP_LASER.getDefaultState())));
        tag.getList("palette",NbtElement.COMPOUND_TYPE).getCompound(0).remove("Properties");
        c.assertTrue(BodyShapeCodec.read(tag).getFirst().state().get(Properties.FACING)==Direction.SOUTH,"old palettes without facing preserve authored +Z barrels");
        var b=body(c,mount(false,Direction.SOUTH,false),at(c),Quat.IDENTITY);
        try{
            var bounds=b.localBlockBounds().get(new BlockPos(0,0,-1));var extent=bounds.max().minus(bounds.min());
            c.assertTrue(extent.x()<.6&&extent.y()<.7&&extent.z()<.6,"lever outline is not replaced by a full invisible cube");
            var cell=new BlockPos(0,0,-1);var center=bounds.centre();var from=b.localToWorld(new Vec3d(center.x(),center.y(),-2));var to=b.localToWorld(new Vec3d(center.x(),center.y(),center.z()));
            c.assertTrue(new BlockBodyWorldAccess(b).raycastBlockBody(from,to).orElseThrow().localBlock().equals(cell),"actual lever proxy is selectable in the local-grid ray");
        }finally{clean(c,b);}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="mounted-lever-auth")
    public void animatedHeadYawCannotRedirectThePlayersActualLeverUse(TestContext c){
        var cell=GUN.up();var b=body(c,Map.of(GUN,gun(false,Direction.SOUTH),cell,lever(Direction.DOWN,false)),at(c),Quat.IDENTITY);var p=player(c,b);
        try{
            p.setPosition(b.localToWorld(new Vec3d(.5,0,-2)));var bounds=b.localBlockBounds().get(cell);var point=b.localToWorld(new Vec3d(bounds.centre().x(),bounds.centre().y(),bounds.centre().z()));
            var look=point.subtract(p.getEyePos()).normalize();var angles=com.terminaldetector.drmd.client.portal.PortalTransform.vectorToYawPitch(pure(look));p.setYaw((float)angles.yawDegrees());p.setPitch((float)angles.pitchDegrees());p.setHeadYaw(p.getYaw()+90);
            c.assertTrue(p.getRotationVec(1).dotProduct(look)<.3,"fixture retains an independently lagging animated head");
            var edit=new BodyBlockInteraction.Edit(b.getUuid(),b.shapeRevision(),cell,BodyBlockInteraction.USE);
            c.assertTrue(BodyBlockInteraction.receive(p,edit)&&b.getLocalBlock(cell).get(Properties.POWERED),"the actual player view, not its animated head, controls the real lever");
            c.assertTrue(!BodyBlockInteraction.receive(p,edit),"view correction does not admit stale material revisions");
        }finally{clean(c,b);p.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="mounted-lever-auth")
    public void leverUseChangesActualMaterialWhileWrongSupportAndSpectatorsDoNotControlGuns(TestContext c){
        var cells=mount(false,Direction.SOUTH,false);var pos=new BlockPos(0,0,-1);var b=body(c,cells,at(c),Quat.IDENTITY);var p=player(c,b);
        try{
            c.assertTrue(b.canUseLocalBlock(pos)&&b.useLocalBlock(p,pos)&&b.getLocalBlock(pos).get(Properties.POWERED),"ordinary empty-hand local-block adapter toggles real lever state");
            p.changeGameMode(GameMode.SPECTATOR);c.assertTrue(!b.useLocalBlock(p,pos)&&b.getLocalBlock(pos).get(Properties.POWERED),"spectator cannot alter the control material");
            b.setLocalBlock(pos,lever(Direction.NORTH,true));c.assertTrue(!BodyMountedWeapons.locallyControlled(b,GUN),"nearby lever attached away from gun does not grant control");
        }finally{p.discard();clean(c,b);}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="mounted-cadence",tickLimit=180)
    public void realPostContactScheduleUsesIndependentLaserRocketCooldownsAndOffDoesNotResetThem(TestContext c){admitted(c,lease->{
        var cells=heavy(mount(false,Direction.SOUTH,true));var rocket=new BlockPos(3,0,0);cells.put(rocket,gun(true,Direction.SOUTH));cells.put(rocket.north(),lever(Direction.SOUTH,true));
        var b=body(c,cells,at(c),Quat.IDENTITY);long start=c.getTick();long[] first={-1};int[] previous={0};var times=new ArrayList<Long>();
        return ()->{
            if(b.isRemoved())return;int lasers=(int)shots(c,b).stream().filter(s->s.getMeshKind()==0).count();
            if(lasers>previous[0]){times.add(c.getTick());previous[0]=lasers;if(first[0]<0)first[0]=c.getTick();}
            if(first[0]>=0&&c.getTick()==first[0]+1)b.setLocalBlock(new BlockPos(0,0,-1),lever(Direction.SOUTH,false));
            if(first[0]>=0&&c.getTick()==first[0]+5)b.setLocalBlock(new BlockPos(0,0,-1),lever(Direction.SOUTH,true));
            if(c.getTick()-start<40)return;
            try{
                var actual=shots(c,b);long rockets=actual.stream().filter(s->s.getMeshKind()==1).count();
                c.assertTrue(times.size()>=4&&rockets==2,"actual scheduler emits repeated lasers and independent24-tick rockets: "+times+" rockets="+rockets);
                for(int i=1;i<times.size();i++)c.assertTrue(times.get(i)-times.get(i-1)>=8,"off/on cannot reset a gun's eight-tick material cooldown");
                c.assertTrue(BodyMountedWeapons.stats(c.getWorld()).checks()<=64&&BodyMountedWeapons.stats(c.getWorld()).shots()<=32,"world work remains bounded");
            }finally{clean(c,b);lease.close();}c.complete();
        };
    });}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="mounted-real-hit",tickLimit=160)
    public void ordinaryLeverPoweredBodyDamagesARealEntityWithoutAFakePilot(TestContext c){admitted(c,lease->{
        var origin=at(c);var b=body(c,heavy(mount(false,Direction.SOUTH,true)),origin,Quat.IDENTITY);
        var target=EntityType.HUSK.create(c.getWorld());target.setAiDisabled(true);target.setNoGravity(true);target.setPosition(origin.add(.5,-.5,8.5));c.assertTrue(c.getWorld().spawnEntity(target),"real damage target");long started=c.getTick();
        return ()->{
            if(b.isRemoved())return;if(target.getHealth()>=20&&c.getTick()-started<30)return;
            try{c.assertTrue(target.getHealth()<20&&target.getRecentDamageSource()!=null&&target.getRecentDamageSource().getSource()==b,"naturally scheduled projectile damage retains the physical gun owner: health="+target.getHealth()+" source="+target.getRecentDamageSource());}
            finally{target.discard();clean(c,b);lease.close();}c.complete();
        };
    });}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="mounted-off",tickLimit=150)
    public void offAndUnattachedLeversNeverCreateScheduledShots(TestContext c){admitted(c,lease->{
        var b=body(c,heavy(mount(false,Direction.SOUTH,false)),at(c),Quat.IDENTITY);long start=c.getTick();
        return ()->{if(b.isRemoved())return;if(c.getTick()-start==12){c.assertTrue(shots(c,b).isEmpty(),"default off weapon has no autonomous trigger");b.setLocalBlock(new BlockPos(0,0,-1),lever(Direction.NORTH,true));}if(c.getTick()-start<24)return;try{c.assertTrue(shots(c,b).isEmpty(),"powered lever mounted away from the gun cannot emit scheduled shots");}
            finally{clean(c,b);lease.close();}c.complete();};
    });}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="mounted-fragment-state",tickLimit=170)
    public void gunCooldownFollowsActualMaterialIntoAFragmentAndSurvivesItsCodecRoundTrip(TestContext c){admitted(c,lease->{
        var cells=new LinkedHashMap<BlockPos,BlockState>();for(int x=-2;x<=1;x++)cells.put(new BlockPos(x,0,0),Blocks.IRON_BLOCK.getDefaultState());var slot=new BlockPos(2,0,0);cells.put(slot,gun(false,Direction.SOUTH));cells.put(slot.north(),lever(Direction.SOUTH,true));
        var b=body(c,cells,at(c),Quat.IDENTITY);long start=c.getTick();
        return ()->{
            if(b.isRemoved())return;var saved=b.mountedWeapons().write(b);if(saved.getList("cooldowns",NbtElement.COMPOUND_TYPE).isEmpty()&&c.getTick()-start<20)return;
            var before=new HashSet<>(BodySpatialIndex.loaded(c.getWorld()));BlockBodyEntity fragment=null;
            try{
                var cooldown=saved.getList("cooldowns",NbtElement.COMPOUND_TYPE);c.assertTrue(cooldown.size()==1,"a real scheduled shot created material cooldown state");
                c.assertTrue(b.setLocalBlock(new BlockPos(1,0,0),Blocks.AIR.getDefaultState()),"actual connecting material is cut");
                fragment=BodySpatialIndex.loaded(c.getWorld()).stream().filter(x->!before.contains(x)&&x.getLocalBlock(slot).isOf(ModWorldBlocks.SHIP_LASER)).findFirst().orElseThrow();
                var after=fragment.mountedWeapons().write(fragment);c.assertTrue(after.getList("cooldowns",NbtElement.COMPOUND_TYPE).equals(cooldown),"exact remaining timer transfers to the actual gun fragment");
                c.assertTrue(b.mountedWeapons().write(b).getList("cooldowns",NbtElement.COMPOUND_TYPE).isEmpty(),"former parent cannot keep duplicate firing authority");
                var read=new BodyMountedWeapons();read.read(fragment,after);c.assertTrue(read.write(fragment).equals(after),"actual material codec restores its timer without a live-body alias");
            }finally{if(fragment!=null)clean(c,fragment);clean(c,b);lease.close();}c.complete();
        };
    });}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="mounted-budget",tickLimit=180)
    public void boundedWorldBudgetEventuallyServesEveryIndependentArmedBody(TestContext c){admitted(c,lease->{
        var origin=at(c);var bodies=new ArrayList<BlockBodyEntity>();var fired=new HashSet<UUID>();long start=c.getTick();
        for(int k=-1;k<=1;k++){
            var cells=new LinkedHashMap<BlockPos,BlockState>();for(int x=0;x<8;x++)for(int z=0;z<4;z++){
                var slot=new BlockPos(x*2,0,z*3);cells.put(slot,gun(false,Direction.SOUTH));cells.put(slot.north(),lever(Direction.SOUTH,true));
            }bodies.add(body(c,cells,origin.add(0,0,k*20),Quat.IDENTITY));
        }
        return ()->{
            if(bodies.getFirst().isRemoved())return;
            var stats=BodyMountedWeapons.stats(c.getWorld());c.assertTrue(stats.checks()<=64&&stats.shots()<=32,"real production per-world admission bounds");
            for(var b:bodies)if(!b.mountedWeapons().write(b).getList("cooldowns",NbtElement.COMPOUND_TYPE).isEmpty())fired.add(b.getUuid());
            if(fired.size()<3&&c.getTick()-start<20)return;
            try{c.assertTrue(fired.size()==3,"bounded schedule serves all three actual weapon bodies without starvation");}
            finally{bodies.forEach(b->clean(c,b));lease.close();}c.complete();
        };
    });}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="mounted-placement")
    public void survivalPlacementUsesLocalBarrelAxisOnARolledBody(TestContext c){
        var q=ImmPtlQuaternions.rotationByRadians(new Vec3(0,0,1),.7);var b=body(c,Map.of(GUN,Blocks.IRON_BLOCK.getDefaultState()),at(c),q);var p=player(c,b);p.changeGameMode(GameMode.SURVIVAL);
        try{
            var aim=b.localToWorld(new Vec3d(.5,.5,.5)).subtract(p.getEyePos()).normalize();var angles=com.terminaldetector.drmd.client.portal.PortalTransform.vectorToYawPitch(pure(aim));p.setYaw((float)angles.yawDegrees());p.setHeadYaw((float)angles.yawDegrees());p.setPitch((float)angles.pitchDegrees());
            var target=BodyBlockInteraction.target(p,p.getEyePos(),p.getRotationVec(1),p.getBlockInteractionRange()).orElseThrow();var hit=target.hit();
            p.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModWorldBlocks.SHIP_LASER,2));c.assertTrue(BodyBlockInteraction.receive(p,new BodyBlockInteraction.Edit(b.getUuid(),hit.revision(),hit.localBlock(),BodyBlockInteraction.PLACE)),"ordinary placement packet constructs a real gun");
            c.assertTrue(b.getLocalBlock(hit.localBlock().offset(hit.localFace())).get(Properties.FACING)==hit.localFace()&&p.getMainHandStack().getCount()==1,"placement orients the barrel in the local hit frame and consumes one item");
        }finally{p.discard();clean(c,b);}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="mounted-lever-placement")
    public void survivalLeverPlacementActuallyMountsToTheStruckGunInItsLocalFrame(TestContext c){
        var q=ImmPtlQuaternions.rotationByRadians(new Vec3(0,0,1),.7);var b=body(c,Map.of(GUN,gun(false,Direction.SOUTH)),at(c),q);var p=player(c,b);p.changeGameMode(GameMode.SURVIVAL);
        try{
            var aim=b.localToWorld(new Vec3d(.5,.5,.5)).subtract(p.getEyePos()).normalize();var angles=com.terminaldetector.drmd.client.portal.PortalTransform.vectorToYawPitch(pure(aim));p.setYaw((float)angles.yawDegrees());p.setHeadYaw((float)angles.yawDegrees());p.setPitch((float)angles.pitchDegrees());
            var hit=BodyBlockInteraction.target(p,p.getEyePos(),p.getRotationVec(1),p.getBlockInteractionRange()).orElseThrow().hit();p.setStackInHand(Hand.MAIN_HAND,new ItemStack(Blocks.LEVER,2));
            c.assertTrue(BodyBlockInteraction.receive(p,new BodyBlockInteraction.Edit(b.getUuid(),hit.revision(),hit.localBlock(),BodyBlockInteraction.PLACE)),"real inventory packet places the lever");
            var cell=hit.localBlock().offset(hit.localFace());var placed=b.getLocalBlock(cell);
            c.assertTrue(placed.isOf(Blocks.LEVER)&&BodyMountedWeapons.support(cell,placed).equals(hit.localBlock())&&BodyMountedWeapons.locallyControlled(b,GUN),"actual lever support resolves to the struck local gun");
            c.assertTrue(p.getMainHandStack().getCount()==1&&!placed.get(Properties.POWERED),"Survival consumes one actual lever and starts the gun off");
        }finally{p.discard();clean(c,b);}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="mounted-alias")
    public void unspawnedSnapshotsAndRemovedBodiesCannotEmitThroughTheSharedWeaponCore(TestContext c){
        var b=body(c,mount(false,Direction.SOUTH,true),at(c),Quat.IDENTITY);var clone=ModEntities.BLOCK_BODY.create(c.getWorld());
        try{
            clone.readNbt(b.writeNbt(new NbtCompound()));var velocity=clone.physics().linearVelocity();
            c.assertTrue(MountedWeaponEmitter.emit(clone,clone,GUN)==null&&clone.physics().linearVelocity().equals(velocity),"unspawned NBT alias has no physical firing authority or recoil");
            b.discard();c.assertTrue(MountedWeaponEmitter.emit(b,b,GUN)==null,"removed physical owner cannot emit");
        }finally{clone.discard();clean(c,b);}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="mounted-compound",tickLimit=180)
    public void ordinaryTwoAxisMechanismFiresFromItsActualTipAndKeepsBothPhysicalBearings(TestContext c){admitted(c,lease->{
        var origin=at(c);var rootCells=new LinkedHashMap<BlockPos,BlockState>(Map.of(GUN,ModWorldBlocks.MECHANICAL_BEARING.getDefaultState().with(Properties.FACING,Direction.EAST),new BlockPos(-1,0,0),ModWorldBlocks.ROTARY_DRIVE.getDefaultState().with(Properties.FACING,Direction.EAST).with(Properties.POWER,5)));
        for(int x=-4;x<=5;x++)for(int z=-4;z<=5;z++)rootCells.put(new BlockPos(x,-3,z),Blocks.IRON_BLOCK.getDefaultState());rootCells.put(new BlockPos(0,-1,0),Blocks.IRON_BLOCK.getDefaultState());rootCells.put(new BlockPos(0,-2,0),Blocks.IRON_BLOCK.getDefaultState());
        var root=body(c,rootCells,origin,Quat.IDENTITY);
        var middle=body(c,Map.of(GUN,ModWorldBlocks.MECHANICAL_BEARING.getDefaultState().with(Properties.FACING,Direction.WEST),new BlockPos(1,0,0),ModWorldBlocks.MECHANICAL_BEARING.getDefaultState().with(Properties.FACING,Direction.UP),new BlockPos(1,-1,0),ModWorldBlocks.ROTARY_DRIVE.getDefaultState().with(Properties.FACING,Direction.UP).with(Properties.POWER,5)),origin.add(1,0,0),Quat.IDENTITY);
        var tip=body(c,Map.of(GUN,ModWorldBlocks.MECHANICAL_BEARING.getDefaultState().with(Properties.FACING,Direction.DOWN),new BlockPos(0,1,0),gun(false,Direction.SOUTH),new BlockPos(0,1,-1),lever(Direction.SOUTH,true)),origin.add(2,1,0),Quat.IDENTITY);
        c.assertTrue(MechanicalBearingSystem.connect(c.getWorld(),root,GUN,middle,GUN)&&MechanicalBearingSystem.connect(c.getWorld(),middle,new BlockPos(1,0,0),tip,GUN),"ordinary parts create two independent axes");long start=c.getTick();var initial=tip.rotation();
        return ()->{
            if(root.isRemoved()||c.getTick()-start<20)return;
            try{
                c.assertTrue(!shots(c,tip).isEmpty()&&shots(c,root).isEmpty()&&shots(c,middle).isEmpty(),"real post-contact shots belong to the physical armed tip");
                c.assertTrue(Math.abs(ImmPtlQuaternions.dot(initial,tip.rotation()))<.9999,"both real drives articulate the armed component");
                var links=BodyJointRegistry.get(c.getWorld()).forBody(middle.getUuid());c.assertTrue(links.size()==2,"both material bearings survive repeated firing/recoil");
                for(var link:links){var a=link.a().body().equals(root.getUuid())?root:middle;var b=link.b().body().equals(tip.getUuid())?tip:middle;
                    var pa=link.a().point();var pb=link.b().point();c.assertTrue(a.localToWorld(new Vec3d(pa.x(),pa.y(),pa.z())).distanceTo(b.localToWorld(new Vec3d(pb.x(),pb.y(),pb.z())))<.03,"post-shot passive reconciliation preserves actual anchors");}
            }finally{clean(c,tip);clean(c,middle);clean(c,root);lease.close();}c.complete();
        };
    });}
}
