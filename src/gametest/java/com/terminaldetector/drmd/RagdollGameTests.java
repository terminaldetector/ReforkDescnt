package com.terminaldetector.drmd;

import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.entity.RagdollEntity;
import com.terminaldetector.drmd.world.gravity.FieldShape;
import com.terminaldetector.drmd.world.gravity.GravityFields;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

public final class RagdollGameTests implements FabricGameTest {
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
