package com.terminaldetector.drmd;

import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.entity.RagdollEntity;
import com.terminaldetector.drmd.world.gravity.FieldShape;
import com.terminaldetector.drmd.world.gravity.GravityFields;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtString;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

public final class RagdollGameTests implements FabricGameTest {
	@GameTest(templateName=EMPTY_STRUCTURE,batchId="ragdoll-repeated-real-damage",tickLimit=180)
	public void repeatedLethalDamageKeepsOneRegisteredLimitedJetWreck(TestContext context){
		var world=context.getWorld();var base=context.getAbsolutePos(new BlockPos(7,80,7));var origin=Vec3d.ofCenter(base);
		var clock=new CodecClock(context,base);var jet=ModEntities.JET.create(world);
		context.assertTrue(jet!=null,"actual typed JET created");jet.setPosition(origin);jet.setAiDisabled(true);jet.setNoGravity(true);
		var area=new Box(origin,origin).expand(7);var before=new java.util.HashSet<UUID>();
		for(var body:world.getEntitiesByClass(RagdollEntity.class,area,e->true))before.add(body.getUuid());
		RagdollEntity[] corpse={null};int[] stage={0},birthAge={0};long[] birthWorld={0};
		clock.run(()->{
			if(stage[0]==0){if(!clock.ready(jet.getBlockPos()))return;clock.spawn(jet);stage[0]=1;return;}
			if(stage[0]==1){
				if(jet.age<2)return;
				context.assertTrue(jet.damage(jet.getDamageSources().genericKill(),Float.MAX_VALUE),"first actual lethal damage event accepted");
				jet.damage(jet.getDamageSources().generic(),1000);jet.damage(jet.getDamageSources().genericKill(),Float.MAX_VALUE);
				var found=world.getEntitiesByClass(RagdollEntity.class,area,e->!before.contains(e.getUuid()));
				context.assertTrue(found.size()==1,"repeated actual damage requests produce exactly one registered corpse");
				corpse[0]=found.getFirst();clock.track(corpse[0]);birthAge[0]=corpse[0].age;birthWorld[0]=world.getTime();stage[0]=2;return;
			}
			if(corpse[0].age-birthAge[0]<8)return;
			clock.assertOrdinaryTicks(corpse[0],birthAge[0],birthWorld[0],8);
			var found=world.getEntitiesByClass(RagdollEntity.class,area,e->!before.contains(e.getUuid()));
			context.assertTrue(found.size()==1&&found.getFirst()==corpse[0],"same sole replacement persists across ordinary world ticks");
			context.assertTrue(!jet.isAlive()&&jet.isInvisible()&&corpse[0].rigVersion()==2,"dead visible hull is replaced by its own limited JET family");
			assertLimitedJetAngles(context,corpse[0]);clock.complete();
		});
	}

	@GameTest(templateName=EMPTY_STRUCTURE,batchId="ragdoll-limited-rail-wall",tickLimit=180)
	public void realRailLaunchKeepsLimitedJetBehindAnObsidianWallUnderSidewaysGravity(TestContext context){
		var world=context.getWorld();var base=context.getAbsolutePos(new BlockPos(7,80,7));var origin=Vec3d.ofCenter(base);
		var clock=new CodecClock(context,base);var jet=ModEntities.JET.create(world);
		context.assertTrue(jet!=null,"authored rail target created");jet.refreshPositionAndAngles(origin.x,origin.y,origin.z,25,-15);jet.setFlightRoll(37);jet.setAiDisabled(true);jet.setNoGravity(true);
		var wall=base.add(6,0,0);var original=new java.util.LinkedHashMap<BlockPos,net.minecraft.block.BlockState>();
		for(var pos:BlockPos.iterate(wall.add(0,-6,-8),wall.add(0,6,8))){var key=pos.toImmutable();original.put(key,world.getBlockState(key));world.setBlockState(key,Blocks.OBSIDIAN.getDefaultState(),2);}
		clock.onClose(()->original.forEach((pos,state)->world.setBlockState(pos,state,2)));
		var field=UUID.randomUUID();clock.onClose(()->GravityFields.remove(field));
		var player=context.createMockCreativeServerPlayerInWorld();clock.track(player);player.setPosition(origin.add(-2,0,0));player.setYaw(-90);player.setPitch(0);player.setNoGravity(true);
		DescentPlayerData.get(player).setShipAttitude(1,0,0,0,1,0);
		clock.onClose(()->com.terminaldetector.drmd.physics.GravyPhysics.release(player));
		RagdollEntity[] body={null};int[] stage={0},launchAge={0};long[] launchWorld={0};double[] nearestWall={Double.POSITIVE_INFINITY},startX={0},startZ={0};
		clock.run(()->{
			if(stage[0]==0){if(!clock.ready(jet.getBlockPos(),wall))return;clock.spawn(jet);stage[0]=1;return;}
			if(stage[0]==1){
				if(jet.age<2)return;jet.damage(jet.getDamageSources().genericKill(),Float.MAX_VALUE);
				var found=world.getEntitiesByClass(RagdollEntity.class,new Box(origin,origin).expand(4),e->e.flightHullKind()==com.terminaldetector.drmd.entity.FlightEnemyKind.JET);
				context.assertTrue(found.size()==1,"actual banked JET death supplies one articulated rail target");body[0]=found.getFirst();clock.track(body[0]);
				context.assertTrue(body[0].rigVersion()==2&&body[0].rig().joints().stream().allMatch(j->j.angularLimits()!=null),"actual target uses authored angular limits");
				GravityFields.put(new GravityFields.Field(field,world.getRegistryKey(),base,new Vec3d(0,0,-1),32,1,FieldShape.SPHERE,"limited JET rail wall",true));
				var sample=GravityFields.sample(world,body[0].getPos());context.assertTrue(sample!=null&&sample.downDir().z<-.99&&sample.strength()>.1,"real sampled sideways gravity is present");
				var rail=com.terminaldetector.drmd.physics.GravyPhysics.Mode.RAIL;
				context.assertTrue(com.terminaldetector.drmd.physics.GravyPhysics.tryGrab(player,rail,java.util.List.of(body[0]))==1,"existing heavy rail captures actual body");
				var aim=com.terminaldetector.drmd.weapon.core.WeaponCore.aimDir(player).normalize();context.assertTrue(aim.x>.99,"actual rail aims toward wall");
				context.assertTrue(com.terminaldetector.drmd.physics.GravyPhysics.fling(player)==1,"existing production rail service launches the articulated body");
				context.assertTrue(body[0].physicsVelocity().distanceTo(aim.multiply(rail.launchSpeed()))<1e-8,"real launch transfers configured aggregate momentum before ordinary motion caps");
				launchAge[0]=body[0].age;launchWorld[0]=world.getTime();startX[0]=body[0].getX();startZ[0]=body[0].getZ();stage[0]=2;return;
			}
			var wreck=body[0];double maximum=wreck.partBounds().stream().mapToDouble(box->box.maxX).max().orElseThrow();
			context.assertTrue(maximum<=wall.getX()+1e-6,"every actual rotated collider remains on near side of solid wall, maxX="+maximum+" wall="+wall.getX());
			nearestWall[0]=Math.min(nearestWall[0],wall.getX()-maximum);
			if(wreck.age-launchAge[0]<32)return;
			clock.assertOrdinaryTicks(wreck,launchAge[0],launchWorld[0],32);
			context.assertTrue(wreck.getX()>startX[0]+2&&nearestWall[0]<.02,"actual rail motion reached and contacted the wall");
			context.assertTrue(wreck.getZ()<startZ[0]-.05,"ordinary destination solver integrated the sampled sideways force");
			assertLimitedJetAngles(context,wreck);
			for(int i=0;i<wreck.rig().joints().size();i++)context.assertTrue(wreck.simulation().jointError(i)<.08,"wall-contacted authored joint remains attached");
			clock.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "ragdoll-codec-limited", tickLimit = 180)
	public void newJetUsesLimitedFamilyAndRestoresItsPhysicalState(TestContext context) {
		var world=context.getWorld();var base=context.getAbsolutePos(new BlockPos(7,80,7));var origin=Vec3d.ofCenter(base);
		var clock=new CodecClock(context,base);
		var jet=ModEntities.JET.create(world);context.assertTrue(jet!=null,"typed JET created");
		jet.setPosition(origin);jet.setAiDisabled(true);jet.setNoGravity(true);jet.setHealth(1);
		int[] stage={0},ageAtImpulse={0},ageAtRestore={0};
		long[] worldAtImpulse={0},worldAtRestore={0};
		RagdollEntity[] wreck={null},restored={null};NbtCompound[] saved={null};
		clock.run(()->{
			if(stage[0]==0){
				if(!clock.ready(jet.getBlockPos()))return;
				clock.spawn(jet);stage[0]=1;return;
			}
			if(stage[0]==1){
				if(jet.age<2)return;
				context.assertTrue(world.getEntity(jet.getUuid())==jet,"live JET received ordinary registered server ticks");
				jet.damage(jet.getDamageSources().generic(),1000);
				var found=world.getEntitiesByClass(RagdollEntity.class,new Box(origin,origin).expand(4),
						body->body.isAlive()&&body.flightHullKind()==com.terminaldetector.drmd.entity.FlightEnemyKind.JET);
				context.assertTrue(found.size()==1,"real JET death event created exactly one typed physical wreck");
				wreck[0]=found.getFirst();clock.track(wreck[0]);
				context.assertTrue(wreck[0].rigVersionSupported()&&wreck[0].rigVersion()==2,"new JET selects explicit limited family v2");
				context.assertTrue(wreck[0].rig().joints().stream().allMatch(j->j.angularLimits()!=null),"both authored wing joints limited");
				wreck[0].applyPhysicsImpulse(wreck[0].getPos().add(.4,.1,0),new Vec3d(0,0,3));
				ageAtImpulse[0]=wreck[0].age;worldAtImpulse[0]=world.getTime();stage[0]=2;return;
			}
			if(stage[0]==2){
				if(wreck[0].age-ageAtImpulse[0]<6)return;
				clock.assertOrdinaryTicks(wreck[0],ageAtImpulse[0],worldAtImpulse[0],6);
				assertLimitedJetAngles(context,wreck[0]);
				saved[0]=new NbtCompound();wreck[0].writeNbt(saved[0]);
				context.assertTrue(saved[0].getInt("life")>=6,"physical lifetime advanced on the actual world clock");
				wreck[0].discard();stage[0]=3;return;
			}
			if(stage[0]==3){
				if(world.getEntity(wreck[0].getUuid())!=null)return;
				restored[0]=new RagdollEntity(ModEntities.RAGDOLL,world);restored[0].readNbt(saved[0]);
				context.assertTrue(restored[0].getUuid().equals(wreck[0].getUuid()),"restore retains entity identity without a duplicate");
				context.assertTrue(restored[0].rigVersion()==2&&restored[0].rigVersionSupported(),"version survives load");
				context.assertTrue(restored[0].rig().joints().equals(wreck[0].rig().joints()),"authored family frames and limits survive load");
				assertPhysicalRestore(context,saved[0],restored[0]);
				clock.spawn(restored[0]);ageAtRestore[0]=restored[0].age;worldAtRestore[0]=world.getTime();stage[0]=4;return;
			}
			if(restored[0].age-ageAtRestore[0]<6)return;
			clock.assertOrdinaryTicks(restored[0],ageAtRestore[0],worldAtRestore[0],6);
			assertLimitedJetAngles(context,restored[0]);
			var after=new NbtCompound();restored[0].writeNbt(after);
			context.assertTrue(after.getInt("life")>=saved[0].getInt("life")+6,"restored simulation resumes physical lifetime on ordinary ticks");
			com.terminaldetector.drmd.DescentMod.LOGGER.info("U13_CODEC_LIMITED_REAL_TICKS jetAge={} wreckAge={} restoredAge={} lifeBefore={} lifeAfter={} uuid={} worldDelta={}",
					jet.age,wreck[0].age,restored[0].age,saved[0].getInt("life"),after.getInt("life"),restored[0].getUuid(),world.getTime()-worldAtRestore[0]);
			clock.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "ragdoll-codec-legacy", tickLimit = 180)
	public void absentRigVersionAndExplicitOneRetainLegacyFreeJetJoints(TestContext context) {
		var world=context.getWorld();var base=context.getAbsolutePos(new BlockPos(7,80,7));var clock=new CodecClock(context,base);
		var actors=new java.util.ArrayList<RagdollEntity>();var origins=new java.util.ArrayList<Vec3d>();
		for(int i=0;i<2;i++){
			var saved=jetSaveFixture(context,new Vec3d(i*4-2,0,0));
			if(i==1)saved.putInt("rigVersion",1);else saved.remove("rigVersion");
			var loaded=new RagdollEntity(ModEntities.RAGDOLL,world);loaded.readNbt(saved);actors.add(loaded);origins.add(loaded.getPos());
			context.assertTrue(loaded.rigVersionSupported()&&loaded.rigVersion()==1,"missing and explicit v1 retain legacy authority");
			context.assertTrue(loaded.rig().segments().size()==3,"legacy fixture has all three saved JET parts");
			context.assertTrue(loaded.rig().joints().stream().allMatch(j->j.angularLimits()==null),"legacy JET limits not silently migrated");
		}
		int[] stage={0};int[] ages=new int[2];long[] startWorld={0};
		clock.run(()->{
			if(stage[0]==0){
				if(!clock.ready(actors.stream().map(Entity::getBlockPos).toArray(BlockPos[]::new)))return;
				for(int i=0;i<actors.size();i++){var actor=actors.get(i);clock.spawn(actor);actor.applyPhysicsImpulse(actor.getPos(),new Vec3d(2,0,0));ages[i]=actor.age;}
				startWorld[0]=world.getTime();stage[0]=1;return;
			}
			for(int i=0;i<actors.size();i++)if(actors.get(i).age-ages[i]<6)return;
			for(int i=0;i<actors.size();i++){
				var actor=actors.get(i);clock.assertOrdinaryTicks(actor,ages[i],startWorld[0],6);
				context.assertTrue(actor.getX()>origins.get(i).x+.01&&actor.physicsVelocity().x>.01,"legacy body moves from its real impulse during ordinary ticks");
				context.assertTrue(actor.rigVersion()==1&&actor.rig().joints().stream().allMatch(j->j.angularLimits()==null),"actual ticking does not migrate legacy joints");
				var saved=new NbtCompound();actor.writeNbt(saved);context.assertTrue(saved.getInt("life")>=6&&saved.getInt("rigVersion")==1,"supported legacy life and version survive actual ticks/save");
			}
			com.terminaldetector.drmd.DescentMod.LOGGER.info("U13_CODEC_LEGACY_REAL_TICKS absentAge={} explicitAge={} worldDelta={} identities={},{}",
					actors.get(0).age,actors.get(1).age,world.getTime()-startWorld[0],actors.get(0).getUuid(),actors.get(1).getUuid());
			clock.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "ragdoll-codec-opaque", tickLimit = 180)
	public void unsupportedRigMetadataIsOpaqueAndCannotAcquirePhysicsAuthority(TestContext context) {
		var world=context.getWorld();var base=context.getAbsolutePos(new BlockPos(7,80,7));var clock=new CodecClock(context,base);
		java.util.List<NbtElement> versions=java.util.List.of(NbtInt.of(99),NbtInt.of(0),NbtString.of("2"),NbtDouble.of(2));
		var actors=new java.util.ArrayList<RagdollEntity>();var originals=new java.util.ArrayList<NbtCompound>();
		var roundTrips=new java.util.ArrayList<NbtCompound>();var positions=new java.util.ArrayList<Vec3d>();
		var centres=new java.util.ArrayList<com.terminaldetector.drmd.client.portal.PortalTransform.Vec3>();
		for(int i=0;i<versions.size();i++){
			var saved=jetSaveFixture(context,new Vec3d(i*2-3,0,0));saved.put("rigVersion",versions.get(i).copy());saved.putInt("life",37);
			var unknown=new NbtCompound();unknown.putString("futurePhysics","opaque payload");
			var nested=new NbtCompound();nested.putDouble("newAngularState",.25);unknown.put("extension",nested);saved.put("ragdoll",unknown);
			var actor=new RagdollEntity(ModEntities.RAGDOLL,world);actor.readNbt(saved);actors.add(actor);originals.add(saved);positions.add(actor.getPos());centres.add(actor.simulation().centreOfMass());
			context.assertTrue(!actor.rigVersionSupported()&&actor.rigVersion()==0,"future or wrong-typed schema is explicitly refused");
		}
		int[] stage={0};int[] ages=new int[4];long[] startWorld={0};
		clock.run(()->{
			if(stage[0]==0){
				if(!clock.ready(actors.stream().map(Entity::getBlockPos).toArray(BlockPos[]::new)))return;
				for(int i=0;i<actors.size();i++){var actor=actors.get(i);clock.spawn(actor);actor.applyPhysicsImpulse(actor.getPos(),new Vec3d(8,0,0));actor.applyPhysicsTorque(new Vec3d(3,4,5));ages[i]=actor.age;}
				startWorld[0]=world.getTime();stage[0]=1;return;
			}
			if(stage[0]==1){
				for(int i=0;i<actors.size();i++)if(actors.get(i).age-ages[i]<6)return;
				for(int i=0;i<actors.size();i++){
					var actor=actors.get(i);clock.assertOrdinaryTicks(actor,ages[i],startWorld[0],6);
					assertOpaqueRefusal(context,actor,positions.get(i),centres.get(i));
					var saved=new NbtCompound();actor.writeNbt(saved);assertOpaqueFields(context,originals.get(i),saved);roundTrips.add(saved);actor.discard();
				}
				stage[0]=2;return;
			}
			if(stage[0]==2){
				if(actors.stream().anyMatch(actor->world.getEntity(actor.getUuid())!=null))return;
				actors.clear();
				for(int i=0;i<roundTrips.size();i++){
					var actor=new RagdollEntity(ModEntities.RAGDOLL,world);actor.readNbt(roundTrips.get(i));actors.add(actor);clock.spawn(actor);ages[i]=actor.age;
				}
				startWorld[0]=world.getTime();stage[0]=3;return;
			}
			for(int i=0;i<actors.size();i++)if(actors.get(i).age-ages[i]<6)return;
			for(int i=0;i<actors.size();i++){
				var actor=actors.get(i);clock.assertOrdinaryTicks(actor,ages[i],startWorld[0],6);assertOpaqueRefusal(context,actor,positions.get(i),centres.get(i));
				var saved=new NbtCompound();actor.writeNbt(saved);assertOpaqueFields(context,originals.get(i),saved);
			}
			com.terminaldetector.drmd.DescentMod.LOGGER.info("U13_CODEC_OPAQUE_REAL_TICKS variants={} firstWorldActors=4 secondWorldActors=4 restoredAges={},{},{},{} unchangedLife=37 worldDelta={}",
					versions,actors.get(0).age,actors.get(1).age,actors.get(2).age,actors.get(3).age,world.getTime()-startWorld[0]);
			clock.complete();
		});
	}

	/** Synthetic old-save bytes with a valid three-part v1 JET pose, never a manually ticked actor. */
	private static NbtCompound jetSaveFixture(TestContext context,Vec3d offset){
		var entity=new RagdollEntity(ModEntities.RAGDOLL,context.getWorld());
		entity.setPosition(Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(7,80,7))).add(offset));
		var identity=new NbtCompound();entity.writeNbt(identity);identity.remove("ragdoll");
		identity.putInt("hullVersion",1);identity.putString("flightHull","jet");identity.putFloat("hullScale",1);identity.remove("rigVersion");
		entity.readNbt(identity);var saved=new NbtCompound();entity.writeNbt(saved);entity.discard();return saved;
	}

	private static void assertLimitedJetAngles(TestContext context,RagdollEntity actor){
		for(var joint:actor.rig().joints()){
			var angles=com.terminaldetector.drmd.physics.ragdoll.RagdollAngularLimit.angles(actor.simulation().parts().get(joint.first()).body().rotation(),
					actor.simulation().parts().get(joint.second()).body().rotation(),joint.angularLimits());
			context.assertTrue(angles.swing()<=joint.angularLimits().maxSwing()+.01,"actual JET swing remains inside its authored cone");
			context.assertTrue(angles.twist()>=joint.angularLimits().minTwist()-.01&&angles.twist()<=joint.angularLimits().maxTwist()+.01,"actual JET twist remains inside its authored interval");
		}
	}
	private static void assertPhysicalRestore(TestContext context,NbtCompound saved,RagdollEntity restored){
		var parts=saved.getCompound("ragdoll").getList("parts",NbtElement.COMPOUND_TYPE);
		context.assertTrue(parts.size()==restored.simulation().parts().size(),"every physical part survives load");
		for(int i=0;i<parts.size();i++){
			var before=parts.getCompound(i);var after=restored.simulation().parts().get(i).body();
			assertSavedVector(context,before,"p",after.position());assertSavedVector(context,before,"v",after.linearVelocity());assertSavedVector(context,before,"l",after.angularMomentum());
			var q=after.rotation();double dot=before.getDouble("qx")*q.x()+before.getDouble("qy")*q.y()+before.getDouble("qz")*q.z()+before.getDouble("qw")*q.w();
			context.assertTrue(Math.abs(dot)>1-1e-10,"physical quaternion survives load");
		}
	}
	private static void assertSavedVector(TestContext context,NbtCompound saved,String prefix,com.terminaldetector.drmd.client.portal.PortalTransform.Vec3 actual){
		var before=new com.terminaldetector.drmd.client.portal.PortalTransform.Vec3(saved.getDouble(prefix+"x"),saved.getDouble(prefix+"y"),saved.getDouble(prefix+"z"));
		context.assertTrue(before.minus(actual).length()<1e-10,"physical vector survives load: "+prefix);
	}
	private static void assertOpaqueRefusal(TestContext context,RagdollEntity actor,Vec3d position,com.terminaldetector.drmd.client.portal.PortalTransform.Vec3 centre){
		context.assertTrue(!actor.rigVersionSupported()&&actor.rigVersion()==0,"ordinary ticks do not grant refused metadata authority");
		context.assertTrue(actor.getPos().equals(position)&&actor.simulation().centreOfMass().equals(centre),"refused registered actor does not integrate or teleport any physical part");
		context.assertTrue(actor.physicsVelocity().length()==0&&actor.physicsAngularVelocity().length()==0,"refused registered actor receives no impulse or torque");
	}
	private static void assertOpaqueFields(TestContext context,NbtCompound expected,NbtCompound actual){
		for(String key:java.util.List.of("rigVersion","ragdoll","hullVersion","flightHull","hullScale","life"))
			context.assertTrue(expected.get(key).equals(actual.get(key)),"opaque optional metadata survives ordinary ticks and save exactly: "+key);
	}
	private static final class CodecClock implements AutoCloseable{
		private final TestContext context;private final AiFixtureChunks chunks;private final java.util.List<Entity> owned=new java.util.ArrayList<>();
		private final java.util.List<Runnable> cleanup=new java.util.ArrayList<>();
		private int callbacks;private boolean closed;
		CodecClock(TestContext context,BlockPos base){this.context=context;chunks=new AiFixtureChunks(context.getWorld(),base,1);}
		boolean ready(BlockPos... positions){return chunks.entityTicking(positions);}
		void track(Entity actor){owned.add(actor);context.assertTrue(context.getWorld().getEntity(actor.getUuid())==actor,"codec actor is registered in the ordinary ServerWorld");}
		void onClose(Runnable action){cleanup.add(action);}
		void spawn(Entity actor){context.assertTrue(ready(actor.getBlockPos()),"codec actor's real entity-ticking chunk is ready");context.assertTrue(context.getWorld().spawnEntity(actor),"codec actor spawned into the actual ServerWorld");track(actor);}
		void assertOrdinaryTicks(Entity actor,int previousAge,long previousWorldTime,int minimum){
			context.assertTrue(context.getWorld().getEntity(actor.getUuid())==actor&&ready(actor.getBlockPos()),"same codec identity remains in the ordinary ticking world");
			context.assertTrue(actor.age-previousAge>=minimum&&context.getWorld().getTime()-previousWorldTime>=minimum,"entity age and ordinary world clock both advanced without manual tick calls");
		}
		void run(Runnable step){context.runAtEveryTick(()->{if(closed)return;try{
			if(++callbacks>140){String detail=owned.stream().map(actor->actor.getUuid()+" age="+actor.age+" removed="+actor.isRemoved()+" "+chunks.readiness(actor.getBlockPos())).collect(java.util.stream.Collectors.joining(";"));close();context.assertTrue(false,"codec real-clock admission/continuation timed out: "+detail);return;}
			step.run();
		}catch(RuntimeException|Error failure){close();throw failure;}});}
		void complete(){close();context.complete();}
		@Override public void close(){if(closed)return;closed=true;owned.forEach(Entity::discard);cleanup.forEach(Runnable::run);chunks.close();}
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "ragdoll-field-default")
	public void removingTheFieldRestoresWorldDownWithoutErasingRagdollMomentum(TestContext context) {
		var world=context.getWorld();var centre=context.getAbsolutePos(new BlockPos(7,80,7));var origin=Vec3d.ofCenter(centre);
		for(var pos:BlockPos.iterate(centre.add(-6,-6,-6),centre.add(6,6,6)))world.setBlockState(pos,Blocks.AIR.getDefaultState(),2);
		var fresh=RagdollEntity.spawnScout(world,origin,Vec3d.ZERO);context.assertTrue(fresh!=null,"fresh world-gravity scout spawned");
		try {
			context.assertTrue(fresh.simulation().centreVelocity().length()<1e-12,"default comparison starts without residual momentum");fresh.tick();var velocity=fresh.simulation().centreVelocity();
			context.assertTrue(velocity.y()<-.4&&velocity.y()>-.6&&Math.abs(velocity.x())+Math.abs(velocity.z())<1e-6,"the existing no-field9.81m/s2 default remains world-down over50ms");
			context.assertTrue(fresh.getVelocity().multiply(20).distanceTo(new Vec3d(velocity.x(),velocity.y(),velocity.z()))<1e-9,"world-down boundary retains m/s to blocks/tick conversion");
		} finally {fresh.discard();}
		var field=UUID.randomUUID();RagdollEntity moving=null;
		try {
			GravityFields.put(new GravityFields.Field(field,world.getRegistryKey(),centre,new Vec3d(1,0,0),32,1,FieldShape.SPHERE,"ragdoll release"));
			moving=RagdollEntity.spawnScout(world,origin,Vec3d.ZERO);context.assertTrue(moving!=null,"sideways field scout spawned");moving.tick();var sideways=moving.simulation().centreVelocity();
			context.assertTrue(sideways.x()>.4&&Math.abs(sideways.y())<1e-6,"one sideways field step has no inherited world-down impulse");
			GravityFields.remove(field);moving.tick();var released=moving.simulation().centreVelocity();
			context.assertTrue(released.x()>sideways.x()*.9&&released.y()<-.4&&released.y()>-.6,"removal restores default force while retaining prior horizontal momentum");
		} finally {GravityFields.remove(field);if(moving!=null)moving.discard();}
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "ragdoll-field-strength")
	public void localGravityUsesSampledStrengthOnAllSixRagdollAxes(TestContext context) {
		var world=context.getWorld();var centre=context.getAbsolutePos(new BlockPos(7,80,7));var origin=Vec3d.ofCenter(centre);
		for(var pos:BlockPos.iterate(centre.add(-6,-6,-6),centre.add(6,6,6)))world.setBlockState(pos,Blocks.AIR.getDefaultState(),2);
		var field=UUID.randomUUID();float[] powers={.25f,.5f,1f,4f};
		try {
			for(var direction:net.minecraft.util.math.Direction.values()) {
				var axis=Vec3d.of(direction.getVector());double[] speeds=new double[powers.length],strengths=new double[powers.length];
				for(int i=0;i<powers.length;i++) {
					GravityFields.put(new GravityFields.Field(field,world.getRegistryKey(),centre,axis,32,powers[i],FieldShape.SPHERE,"ragdoll strength"));
					strengths[i]=GravityFields.sample(world,origin).strength();
					var ragdoll=RagdollEntity.spawnScout(world,origin,Vec3d.ZERO);context.assertTrue(ragdoll!=null,"real scout rig spawned");
					try {
						context.assertTrue(ragdoll.simulation().centreVelocity().length()<1e-12,"fresh rig has no residual impulse");
						ragdoll.tick();var velocity=ragdoll.simulation().centreVelocity();var actual=new Vec3d(velocity.x(),velocity.y(),velocity.z());speeds[i]=actual.dotProduct(axis);
						// D6 COM velocity is metres/second; ordinary Entity velocity is blocks/tick.
						context.assertTrue(ragdoll.getVelocity().multiply(20).distanceTo(actual)<1e-9,"entity boundary converts physical velocity once");
						context.assertTrue(speeds[i]>.01&&actual.subtract(axis.multiply(speeds[i])).length()<1e-6,"uniform gravity changes COM motion only along its field axis: "+direction);
					} finally {ragdoll.discard();}
				}
				context.assertTrue(Math.abs(strengths[3]-2)<1e-6,"overpowered emitter is sampled through the existing strength cap");
				for(int i=0;i<powers.length;i++)context.assertTrue(Math.abs(speeds[i]-speeds[2]*strengths[i]/strengths[2])<1e-6,"ragdoll acceleration follows sampled field strength: "+direction+" power="+powers[i]+" sampled="+strengths[i]+" speed="+speeds[i]+" baseline="+speeds[2]);
			}
		} finally {GravityFields.remove(field);}
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
	public void scoutRagdollFallsConnectsAndSettles(TestContext context) {
		var world = context.getWorld();
		BlockPos floor = context.getAbsolutePos(new BlockPos(7, 2, 7));
		for (BlockPos pos : BlockPos.iterate(floor.add(-3, 0, -3), floor.add(3, 0, 3)))
			world.setBlockState(pos, Blocks.OBSIDIAN.getDefaultState());

		Vec3d origin = Vec3d.ofCenter(floor).add(0, 4, 0);
		RagdollEntity ragdoll = RagdollEntity.spawnScout(world, origin, new Vec3d(.08, 0, 0));
		context.assertTrue(ragdoll != null, "ragdoll entity spawned");
		double startY = ragdoll.getY();
		for (int tick = 0; tick < 140; tick++) ragdoll.tick();

		context.assertTrue(ragdoll.getY() < startY - 1,
				"gravity moved articulated wreck; startY=" + startY + " finalY=" + ragdoll.getY());
		double floorTop = floor.getY() + 1;
		context.assertTrue(ragdoll.partBounds().stream().allMatch(box -> box.minY >= floorTop - .025),
				"every segment remained above the collision floor");
		for (int joint = 0; joint < ragdoll.rig().joints().size(); joint++)
			context.assertTrue(ragdoll.simulation().jointError(joint) < .08,
					"joint " + joint + " remained attached");
		ragdoll.discard();
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
	public void localGravityPullsRagdollOntoWall(TestContext context) {
		var world = context.getWorld();
		BlockPos wall = context.getAbsolutePos(new BlockPos(3, 5, 7));
		for (BlockPos pos : BlockPos.iterate(wall.add(0, -3, -3), wall.add(0, 3, 3)))
			world.setBlockState(pos, Blocks.OBSIDIAN.getDefaultState());
		Vec3d origin = Vec3d.ofCenter(wall).add(4, 0, 0);
		RagdollEntity ragdoll = RagdollEntity.spawnScout(world, origin, Vec3d.ZERO);
		context.assertTrue(ragdoll != null, "ragdoll entity spawned");

		UUID fieldId = new UUID(0x726167646f6c6cL, wall.asLong());
		GravityFields.put(new GravityFields.Field(fieldId, world.getRegistryKey(), wall,
				new Vec3d(-1, 0, 0), 10, 1f, FieldShape.SPHERE, "ragdoll wall test", true));
		double startX = ragdoll.getX();
		try {
			for (int tick = 0; tick < 120; tick++) ragdoll.tick();
			context.assertTrue(ragdoll.getX() < startX - 1, "local gravity moved the wreck sideways");
			double minimumX = ragdoll.partBounds().stream().mapToDouble(box -> box.minX).min().orElseThrow();
			context.assertTrue(minimumX >= wall.getX() + 1 - .025,
					"segments settled against the wall instead of tunnelling through it; penetration="
							+ (wall.getX() + 1 - minimumX));
		} finally {
			GravityFields.remove(fieldId);
			ragdoll.discard();
		}
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void ragdollStateRoundTripsAndAcceptsImpulse(TestContext context) {
		var world = context.getWorld();
		Vec3d origin = Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(7, 5, 7)));
		RagdollEntity ragdoll = RagdollEntity.spawnScout(world, origin, Vec3d.ZERO);
		context.assertTrue(ragdoll != null, "ragdoll entity spawned");
		var hit = ragdoll.physicsRaycast(origin.add(0, 0, -2), origin.add(0, 0, 2));
		context.assertTrue(hit.isPresent(), "ray hit an occupied ragdoll segment");
		ragdoll.applyPhysicsImpulse(hit.orElseThrow(), new Vec3d(8, 0, 0));
		context.assertTrue(ragdoll.physicsVelocity().x > .1, "impulse reached articulated physics");

		NbtCompound saved = new NbtCompound();
		ragdoll.writeNbt(saved);
		double velocity = ragdoll.physicsVelocity().x;
		ragdoll.discard();
		RagdollEntity restored = new RagdollEntity(ModEntities.RAGDOLL, world);
		restored.readNbt(saved);
		context.assertTrue(restored.simulation().parts().size() == 5, "all scout segments survived NBT");
		context.assertTrue(Math.abs(restored.physicsVelocity().x - velocity) < 1.0e-6,
				"aggregate motion survived NBT");
		restored.discard();
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void destroyedDroneCreatesOnePhysicalWreck(TestContext context) {
		var world = context.getWorld();
		var drone = ModEntities.DRONE.create(world);
		context.assertTrue(drone != null, "drone entity created");
		Vec3d origin = Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(7, 6, 7)));
		drone.setPosition(origin);
		drone.setHealth(1);
		world.spawnEntity(drone);
		drone.damage(drone.getDamageSources().generic(), 1000);
		var wrecks = world.getEntitiesByClass(RagdollEntity.class, new Box(origin, origin).expand(3), Entity::isAlive);
		context.assertTrue(wrecks.size() == 1, "death adapter spawned exactly one ragdoll");
		context.assertTrue(drone.isInvisible(), "old death model no longer overlaps the physical wreck");
		wrecks.forEach(RagdollEntity::discard);
		drone.discard();
		context.complete();
	}
}
