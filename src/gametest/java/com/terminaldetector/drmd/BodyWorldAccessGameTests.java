package com.terminaldetector.drmd;
import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.world.contraption.*;
import com.terminaldetector.drmd.world.contraption.boarding.*;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import com.terminaldetector.drmd.entity.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import net.minecraft.item.*;
import net.minecraft.util.Hand;
import net.minecraft.world.GameMode;
import java.util.*;

public final class BodyWorldAccessGameTests implements FabricGameTest {
 private static final Vec3 ZERO=new Vec3(0,0,0);
 private BlockBodyEntity fixture(TestContext c,Map<BlockPos,BlockState> local) {
  var w=c.getWorld();var at=c.getAbsolutePos(new BlockPos(8,150,8));
  for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)w.getChunk((at.getX()>>4)+x,(at.getZ()>>4)+z);
  for(var p:BlockPos.iterate(at.add(-5,-5,-5),at.add(20,20,20)))w.setBlockState(p,Blocks.AIR.getDefaultState(),2);
  var selected=new LinkedHashMap<BlockPos,BlockState>();local.forEach((p,s)->{w.setBlockState(at.add(p),s,2);selected.put(at.add(p),s);});
  var body=BlockBodyEntity.assembleSelection(w,selected);body.tick();return body;
 }
 private Map<BlockPos,BlockState> iron(){return Map.of(BlockPos.ORIGIN,Blocks.IRON_BLOCK.getDefaultState(),new BlockPos(3,0,0),Blocks.IRON_BLOCK.getDefaultState());}
 private void near(TestContext c,Vec3d a,Vec3d b,String why){c.assertTrue(a.distanceTo(b)<1e-7,why+": "+a+" != "+b);}
 @GameTest(templateName=EMPTY_STRUCTURE,batchId="body-edit-com")
 public void removingMassDuringThreeAxisSpinPreservesMaterialPointsAndTheirVelocity(TestContext c) {
  var body=fixture(c,iron());try {
   var q=ImmPtlQuaternions.rotationByRadians(new Vec3(1,2,3).normalized(),1.3);
   body.physics().withRotation(q).withLinearVelocity(new Vec3(2,.4,.3)).withAngularVelocity(new Vec3(.2,.3,.4));
   var anchor=body.getPos();var before=body.localToWorld(new Vec3d(.5,.5,.5));var velocity=body.surfaceVelocityAt(before);var oldCom=body.massPosition();
   c.assertTrue(new BlockBodyWorldAccess(body).breakLocalBlock(new BlockPos(3,0,0)),"real local removal committed");
   near(c,before,body.localToWorld(new Vec3d(.5,.5,.5)),"remaining geometry cannot jump when COM changes");
   near(c,velocity,body.surfaceVelocityAt(before),"remaining point keeps linear plus angular velocity");near(c,anchor,body.getPos(),"tracked pivot remains fixed");
   c.assertTrue(body.physicsMass()==1&&body.shapeRevision()==1&&body.massPosition().minus(oldCom).length()>1,"mass and actual world COM update");
   c.assertTrue(body.physics().angularVelocity().minus(new Vec3(.2,.3,.4)).length()<1e-8,"removed material carries its momentum without resetting spin");
  }finally{body.discard();}c.complete();
 }
 @GameTest(templateName=EMPTY_STRUCTURE,batchId="body-edit-save")
 public void editedPivotBlocksGravityAndMomentumSurviveARealEntityNbtRoundtrip(TestContext c) {
  var body=fixture(c,iron());var restored=ModEntities.BLOCK_BODY.create(c.getWorld());try {
   body.setPassengerGravity(new Vec3(0,-32,0));body.physics().withRotation(ImmPtlQuaternions.rotationByRadians(new Vec3(1,1,1).normalized(),1.1)).withLinearVelocity(new Vec3(1,2,3)).withAngularVelocity(new Vec3(.1,.2,.3));
   body.setLocalBlock(new BlockPos(3,0,0),Blocks.AIR.getDefaultState());body.setLocalBlock(new BlockPos(0,1,0),Blocks.GOLD_BLOCK.getDefaultState());
   restored.readNbt(body.writeNbt(new net.minecraft.nbt.NbtCompound()));
   near(c,body.localToWorld(new Vec3d(.5,1.5,.5)),restored.localToWorld(new Vec3d(.5,1.5,.5)),"restored grid retains its actual world position");
   c.assertTrue(restored.localPivot().equals(body.localPivot())&&restored.shapeRevision()==2,"fixed pivot and revision are saved");
   c.assertTrue(restored.getLocalBlock(new BlockPos(0,1,0)).isOf(Blocks.GOLD_BLOCK)&&restored.getLocalBlock(new BlockPos(3,0,0)).isAir(),"exact local edits survive");
   c.assertTrue(restored.physics().linearVelocity().minus(body.physics().linearVelocity()).length()<1e-9&&restored.passengerGravity().equals(body.passengerGravity()),"physical state survives");
  }finally{body.discard();restored.discard();}c.complete();
 }
 @GameTest(templateName=EMPTY_STRUCTURE,batchId="body-edit-ray")
 public void raycastFindsTheActualRotatedCellAndLocalFaceAcrossAHole(TestContext c) {
  var body=fixture(c,iron());try {
   body.physics().withRotation(ImmPtlQuaternions.rotationByRadians(new Vec3(1,2,3).normalized(),.9));var access=new BlockBodyWorldAccess(body);
   c.assertTrue(access.raycastBlockBody(body.localToWorld(new Vec3d(1.5,.5,-2)),body.localToWorld(new Vec3d(1.5,.5,3))).isEmpty(),"hole in broadphase stays empty");
   var hit=access.raycastBlockBody(body.localToWorld(new Vec3d(3.5,.5,-2)),body.localToWorld(new Vec3d(3.5,.5,3))).orElseThrow();
   c.assertTrue(hit.localBlock().equals(new BlockPos(3,0,0))&&hit.localFace()==Direction.NORTH,"hit selects the material cell and local face");
   c.assertTrue(hit.worldNormal().minus(body.rotation().rotate(new Vec3(0,0,-1))).length()<1e-8,"normal transformed by full quaternion");
  }finally{body.discard();}c.complete();
 }
 @GameTest(templateName=EMPTY_STRUCTURE,batchId="body-edit-cache")
 public void minorDamageReusesDistantNaturalMeshesInsteadOfRebuildingEverySurface(TestContext c) {
  var body=fixture(c,Map.of(BlockPos.ORIGIN,Blocks.STONE.getDefaultState(),new BlockPos(8,0,0),Blocks.STONE.getDefaultState(),new BlockPos(9,0,0),Blocks.STONE.getDefaultState()));try {
   var far=body.cells().stream().filter(x->x.pos().getX()==8).findFirst().orElseThrow();var mesh=body.surface(far);
   var receiver=ModEntities.BLOCK_BODY.create(c.getWorld());receiver.readNbt(body.writeNbt(new net.minecraft.nbt.NbtCompound()));
   var received=receiver.surface(far);
   c.assertTrue(body.setLocalBlock(BlockPos.ORIGIN,Blocks.IRON_BLOCK.getDefaultState()),"bounded edit commits");
   c.assertTrue(body.surface(far)==mesh,"unaffected cached surface retains object identity");
   try {receiver.readNbt(body.writeNbt(new net.minecraft.nbt.NbtCompound()));c.assertTrue(receiver.surface(far)==received,"receiving an updated shape also preserves unchanged meshes");}
   finally{receiver.discard();}
   c.assertTrue(body.collider().bounds().isPresent(),"local spatial index is refreshed");
  }finally{body.discard();}c.complete();
 }
 @GameTest(templateName=EMPTY_STRUCTURE,batchId="body-edit-place")
 public void localPlacementRejectsTerrainAndPlayerOccupancyWithoutChangingWorldBlocks(TestContext c) {
  var body=fixture(c,iron());var p=c.createMockCreativeServerPlayerInWorld();try {
   var pos=new BlockPos(1,0,0);var world=BlockPos.ofFloored(body.localToWorld(new Vec3d(1.5,.5,.5)));var access=new BlockBodyWorldAccess(body);
   c.getWorld().setBlockState(world,Blocks.IRON_BLOCK.getDefaultState(),2);
   c.assertTrue(!access.placeLocalBlock(pos,Blocks.GOLD_BLOCK.getDefaultState())&&body.shapeRevision()==0,"occupied world cube cannot be overwritten");
   c.getWorld().setBlockState(world,Blocks.AIR.getDefaultState(),2);p.setPosition(body.localToWorld(new Vec3d(1.5,0,.5)));
   c.assertTrue(!access.placeLocalBlock(pos,Blocks.GOLD_BLOCK.getDefaultState()),"cannot place inside player");p.setPosition(body.getPos().add(0,5,0));
   c.assertTrue(access.placeLocalBlock(pos,Blocks.GOLD_BLOCK.getDefaultState()),"free local cube accepts item geometry");
   c.assertTrue(c.getWorld().getBlockState(world).isAir(),"ship placement never creates a vanilla world block");
   c.assertTrue(!body.setLocalBlock(new BlockPos(BlockBodyEntity.MAX_LOCAL,0,0),Blocks.IRON_BLOCK.getDefaultState())&&!body.setLocalBlock(pos,Blocks.CHEST.getDefaultState()),"bounds and unsupported functional blocks are rejected");
  }finally{body.discard();p.discard();}c.complete();
 }
 @GameTest(templateName=EMPTY_STRUCTURE,batchId="body-edit-auth")
 public void serverEditRecastsAimRejectsStaleRevisionAndChargesTheActualHeldStack(TestContext c) {
  var body=fixture(c,iron());var p=c.createMockCreativeServerPlayerInWorld();try {
   DescentPlayerData.get(p).setEnabled(false);p.changeGameMode(GameMode.SURVIVAL);p.setPosition(body.localToWorld(new Vec3d(.5,1.4,-2)));p.setYaw(0);p.setPitch(60);
   var eye=p.getEyePos();var aim=body.localToWorld(new Vec3d(.5,.5,.5)).subtract(eye).normalize();var angles=com.terminaldetector.drmd.client.portal.PortalTransform.vectorToYawPitch(BoardingSystem.pure(aim));p.setYaw((float)angles.yawDegrees());p.setPitch((float)angles.pitchDegrees());
   var target=BodyBlockInteraction.target(p,p.getEyePos(),p.getRotationVec(1),p.getBlockInteractionRange()).orElseThrow();
   p.setStackInHand(Hand.MAIN_HAND,new ItemStack(Blocks.GOLD_BLOCK,3));var e=new BodyBlockInteraction.Edit(body.getUuid(),0,target.hit().localBlock(),BodyBlockInteraction.PLACE);
   var edited=BodyBlockInteraction.receive(p,e);
   c.assertTrue(edited,"actual held item places against authoritative ray: mode="+p.interactionManager.getGameMode()+" alive="+p.isAlive()+" vehicle="+p.hasVehicle()+" actor="+p.getBoundingBox()+" revision="+body.shapeRevision()+" first="+target+" current="+BodyBlockInteraction.target(p,p.getEyePos(),p.getRotationVec(1),p.getBlockInteractionRange())+" entities="+c.getWorld().getOtherEntities(body,body.getBoundingBox(),x->true));c.assertTrue(p.getMainHandStack().getCount()==2,"survival consumes one actual item: count="+p.getMainHandStack().getCount()+" isCreative="+p.isCreative()+" mode="+p.interactionManager.getGameMode()+" abilities="+p.getAbilities().creativeMode);
   c.assertTrue(!BodyBlockInteraction.receive(p,e)&&p.getMainHandStack().getCount()==2,"stale replay cannot consume or edit again");
   var forged=new BodyBlockInteraction.Edit(body.getUuid(),body.shapeRevision(),new BlockPos(3,0,0),BodyBlockInteraction.MINE);
   c.assertTrue(!BodyBlockInteraction.receive(p,forged),"client local position cannot replace the actual current hit");
  }finally{body.discard();p.discard();}c.complete();
 }
 @GameTest(templateName=EMPTY_STRUCTURE,batchId="body-edit-pick")
 public void ordinaryMinecraftPickingUsesTheHoleInsteadOfTheHullBoundingBox(TestContext c) {
  var body=fixture(c,iron());var p=c.createMockCreativeServerPlayerInWorld();try {
   var start=body.localToWorld(new Vec3d(1.5,.5,-2));var end=body.localToWorld(new Vec3d(1.5,.5,3));
   var hit=net.minecraft.entity.projectile.ProjectileUtil.raycast(p,start,end,new Box(start,end).expand(1),e->e.canHit(),25);
   c.assertTrue(hit==null,"ordinary crosshair does not hit empty hull volume");
   start=body.localToWorld(new Vec3d(3.5,.5,-2));end=body.localToWorld(new Vec3d(3.5,.5,3));
   hit=net.minecraft.entity.projectile.ProjectileUtil.raycast(p,start,end,new Box(start,end).expand(1),e->e.canHit(),25);
   c.assertTrue(hit!=null&&hit.getEntity()==body,"ordinary crosshair still selects real hull material");
  }finally{body.discard();p.discard();}c.complete();
 }
 @GameTest(templateName=EMPTY_STRUCTURE,batchId="body-index-pick")
 public void ordinaryPickingFindsAnEditedWingOutsideThePivotEntitySection(TestContext c) {
  var body=fixture(c,iron());var p=c.createMockCreativeServerPlayerInWorld();try {
   body.setPosition((body.getBlockX()&~15)+2.5,body.getY(),body.getZ());
   c.assertTrue(body.setLocalBlock(new BlockPos(23,0,0),Blocks.IRON_BLOCK.getDefaultState()),"extend a valid local wing");
   var start=body.localToWorld(new Vec3d(23.5,.5,-2));var end=body.localToWorld(new Vec3d(23.5,.5,3));
   c.assertTrue(body.raycast(start,end).isPresent(),"actual local wing geometry intersects the ray");
   var hit=net.minecraft.entity.projectile.ProjectileUtil.raycast(p,start,end,new Box(start,end).expand(1),e->e.canHit(),25);
   c.assertTrue(hit!=null&&hit.getEntity()==body,"crosshair must find the wing even when its pivot section is outside the ray query");
  }finally{body.discard();p.discard();}c.complete();
 }
 @GameTest(templateName=EMPTY_STRUCTURE,batchId="body-edit-unloaded")
 public void placementRejectsAnotherHullsFarWingWithoutCreatingAWorldBlock(TestContext c) {
  var body=fixture(c,iron());var other=fixture(c,iron());try {
   body.setPosition((body.getBlockX()&~15)+4.5,body.getY(),body.getZ());
   c.assertTrue(other.setLocalBlock(new BlockPos(23,0,0),Blocks.IRON_BLOCK.getDefaultState()),"other hull has a far wing");
   var destination=body.localToWorld(new Vec3d(1.5,.5,.5));
   other.setPosition(destination.subtract(new Vec3d(23.5-other.localPivot().x(),.5-other.localPivot().y(),.5-other.localPivot().z())));
   var access=new BlockBodyWorldAccess(body);
   c.assertTrue(!access.placeLocalBlock(new BlockPos(1,0,0),Blocks.GOLD_BLOCK.getDefaultState()),"placement cannot overlap the far end of a second hull");
   other.discard();
   c.assertTrue(access.placeLocalBlock(new BlockPos(1,0,0),Blocks.GOLD_BLOCK.getDefaultState()),"unloaded/removed hull cannot leave a phantom collider");
   c.assertTrue(c.getWorld().getBlockState(BlockPos.ofFloored(destination)).isAir(),"local edit never writes the world cell");
  }finally{body.discard();other.discard();}c.complete();
 }
 @GameTest(templateName=EMPTY_STRUCTURE,batchId="body-index-lifecycle")
 public void indexIncludesRotatedGravityFieldCornersOfADamagedSparseHull(TestContext c) {
  var body=fixture(c,Map.of(BlockPos.ORIGIN,Blocks.IRON_BLOCK.getDefaultState()));try {
   for(var pos:List.of(new BlockPos(23,0,0),new BlockPos(0,23,0),new BlockPos(0,0,23)))
    c.assertTrue(body.setLocalBlock(pos,Blocks.IRON_BLOCK.getDefaultState()),"three surviving hull arms fit the supported grid");
   body.physics().withRotation(com.terminaldetector.drmd.client.portal.PortalTransform.rotationBetween(new Vec3(1,1,1).normalized(),new Vec3(0,1,0)));
   body.setPassengerGravity(new Vec3(0,-32,0));body.publishPhysicsState();
   var at=body.localToWorld(new Vec3d(27.5,27.5,27.5));
   c.assertTrue(body.boardingMotion().gravity(BoardingSystem.pure(at),1).isPresent(),"actual finite field includes its rotated empty corner");
   c.assertTrue(BodySpatialIndex.query(c.getWorld(),new Box(at,at).expand(4)).contains(body),"boarding broadphase must not lose the true field corner of a sparse rotated hull");
  }finally{body.discard();}c.complete();
 }
 @GameTest(templateName=EMPTY_STRUCTURE,batchId="body-index-lifecycle")
 public void bodyIndexFollowsMovementUntrackingAndReloadWithoutLoadingChunks(TestContext c) {
  var body=fixture(c,iron());try {
   var old=body.getBoundingBox();c.assertTrue(BodySpatialIndex.query(c.getWorld(),old).contains(body),"actual entity-load enrolled the hull");
   int loaded=c.getWorld().getChunkManager().getLoadedChunkCount();
   body.setPosition(body.getPos().add(16,0,16));body.tick();
   c.assertTrue(!BodySpatialIndex.query(c.getWorld(),old).contains(body),"old spatial extent expires after the next physical tick");
   var at=body.getBoundingBox();c.assertTrue(BodySpatialIndex.query(c.getWorld(),at).contains(body),"new position is searchable in all axes");
   BodySpatialIndex.query(c.getWorld(),at.offset(2048,0,2048));
   BodySpatialIndex.untrack(body);c.assertTrue(!BodySpatialIndex.query(c.getWorld(),at).contains(body),"entity unload removes the leaf");
   BodySpatialIndex.track(body);c.assertTrue(BodySpatialIndex.query(c.getWorld(),at).contains(body),"entity load restores exactly one leaf");
   c.assertTrue(c.getWorld().getChunkManager().getLoadedChunkCount()==loaded,"spatial queries/move into an unknown region cannot force chunk generation");
  }finally{body.discard();}c.complete();
 }
 @GameTest(templateName=EMPTY_STRUCTURE,batchId="body-edit-unloaded")
 public void interactionRayCannotGenerateUnknownGeometry(TestContext c) {
  var p=c.createMockCreativeServerPlayerInWorld();try {
   int x=100000,z=100000;var manager=c.getWorld().getChunkManager();
   c.assertTrue(manager.getWorldChunk(x>>4,z>>4)==null,"remote column starts unloaded");
   c.assertTrue(BodyBlockInteraction.target(p,new Vec3d(x,150,z),new Vec3d(1,0,0),6).isEmpty(),"unknown interaction volume is closed");
   c.assertTrue(manager.getWorldChunk(x>>4,z>>4)==null,"ray cannot join a chunk future or generate a column");
  }finally{p.discard();}c.complete();
 }

 @GameTest(templateName=EMPTY_STRUCTURE,batchId="body-edit-mining",tickLimit=120)
 public void survivalMiningNeedsRealTicksCanBeCancelledAndDropsOnlyTheRemovedLocalBlock(TestContext c) {
  var body=fixture(c,Map.of(BlockPos.ORIGIN,Blocks.STONE.getDefaultState(),new BlockPos(3,0,0),Blocks.IRON_BLOCK.getDefaultState()));
  var p=c.createMockCreativeServerPlayerInWorld();p.changeGameMode(GameMode.SURVIVAL);DescentPlayerData.get(p).setEnabled(false);
  p.getAbilities().allowFlying=true;p.getAbilities().flying=true;p.sendAbilitiesUpdate();p.setPosition(body.localToWorld(new Vec3d(.5,2.4,.5)));p.setPitch(90);p.setYaw(0);
  p.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.DIAMOND_PICKAXE));
  var edit=new BodyBlockInteraction.Edit(body.getUuid(),0,BlockPos.ORIGIN,BodyBlockInteraction.MINE);
  var region=body.getBoundingBox().expand(20);var existing=new HashSet<java.util.UUID>();
  for(var e:c.getWorld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class,region,e->true))existing.add(e.getUuid());
  for(int tick=1;tick<=60;tick++) {int n=tick;c.runAtTick(tick,()->{
   if(n<=3||n>=21) {if(!body.getLocalBlock(BlockPos.ORIGIN).isAir())BodyBlockInteraction.receive(p,edit);}
   if(n==4)BodyBlockInteraction.receive(p,new BodyBlockInteraction.Edit(body.getUuid(),0,BlockPos.ORIGIN,BodyBlockInteraction.STOP));
   if(n==3||n==20)c.assertTrue(!body.getLocalBlock(BlockPos.ORIGIN).isAir(),"mining cannot break instantly or continue after release");
  });}
  c.runAtTick(65,()->{try {
   c.assertTrue(body.getLocalBlock(BlockPos.ORIGIN).isAir()&&body.getLocalBlock(new BlockPos(3,0,0)).isOf(Blocks.IRON_BLOCK),"server mining changes only the aimed local cell");
   c.assertTrue(p.getMainHandStack().getDamage()==1,"one local mined block charges normal tool durability");
   var dropped=c.getWorld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class,region,e->!existing.contains(e.getUuid()));
   c.assertTrue(dropped.size()==1&&dropped.get(0).getStack().isOf(Blocks.COBBLESTONE.asItem())&&dropped.get(0).getStack().getCount()==1,"actual vanilla loot is emitted exactly once");dropped.forEach(net.minecraft.entity.Entity::discard);
   c.complete();
  }finally{body.discard();p.discard();}});
 }

 @GameTest(templateName=EMPTY_STRUCTURE,batchId="body-edit-portal")
 public void portalExitChecksTheEditedMassOffsetAndTransformsTheWholeLocalGrid(TestContext c) {
  var body=fixture(c,iron());try {
   body.setLocalBlock(new BlockPos(3,0,0),Blocks.AIR.getDefaultState());
   var from=BoardingSystem.pure(body.getPos());var exit=from.plus(new Vec3(8,0,8));
   var transform=com.terminaldetector.drmd.d6.D6PortalTransform.of(from,new Vec3(0,0,1),exit,new Vec3(1,0,0));
   var com=exit.plus(transform.transformOrientation(body.rotation()).rotate(body.localCentre().minus(body.localPivot())));
   var obstacle=BlockPos.ofFloored(com.x(),com.y(),com.z());c.getWorld().setBlockState(obstacle,Blocks.IRON_BLOCK.getDefaultState(),2);
   c.assertTrue(!body.canCrossPortal(transform,exit),"edited COM offset must not hide an occupied portal exit");
   c.getWorld().setBlockState(obstacle,Blocks.AIR.getDefaultState(),2);c.assertTrue(body.canCrossPortal(transform,exit),"same edited grid fits a clear exit");
   var before=body.localToWorld(new Vec3d(.5,.5,.5));body.crossPortal(transform);
   near(c,body.getPos(),BoardingSystem.mc(exit),"portal maps the stable grid pivot once");
   near(c,body.localToWorld(new Vec3d(.5,.5,.5)),BoardingSystem.mc(transform.transformPoint(BoardingSystem.pure(before))),"material point receives the same portal transform");
  }finally{body.discard();}c.complete();
 }
 @GameTest(templateName=EMPTY_STRUCTURE,batchId="body-edit-explosion")
 public void blastUsesTheEditedPhysicalCentreAndHomingAimStaysOnMaterial(TestContext c) {
  var body=fixture(c,iron());var p=c.createMockCreativeServerPlayerInWorld();try {
   body.setLocalBlock(new BlockPos(3,0,0),Blocks.AIR.getDefaultState());
   c.assertTrue(body.physicsCentre().distanceTo(body.getPos())>1,"edited body separates COM and tracking pivot");
   var local=body.worldToLocal(body.physicsAimPoint());c.assertTrue(local.distanceTo(new Vec3d(.5,.5,.5))<1e-8,"homing aims at occupied material rather than the emptied grid pivot");
   com.terminaldetector.drmd.weapon.core.WeaponCore.splashDamage(p,c.getWorld(),body.physicsCentre().add(0,0,-1),30,3,com.terminaldetector.drmd.weapon.core.DamageClass.KINETIC);
   c.assertTrue(body.physics().linearVelocity().length()>0&&body.physics().angularMomentum().length()<1e-8,"centre blast translates the edited hull without artificial pivot torque");
  }finally{body.discard();p.discard();}c.complete();
 }

}
