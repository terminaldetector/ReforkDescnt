package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.d6.D6PortalTransform;
import com.terminaldetector.drmd.entity.RagdollEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/** Real existing entity state and normal world clocks; helper arithmetic has its own unit cases. */
public final class RagdollPortalTransferGameTests implements FabricGameTest {
	@GameTest(templateName=EMPTY_STRUCTURE,batchId="ragdoll-portal-real-state",tickLimit=180)
	public void articulatedTransferCommitsOnceAndContinuesOnOrdinaryTicks(TestContext context){
		var world=context.getWorld();var base=context.getAbsolutePos(new BlockPos(7,80,7));
		var chunks=new AiFixtureChunks(world,base,1);
		RagdollEntity[] actor={null};int[] stage={0},callbacks={0},initialAge={0},arrivalAge={0},arrivalLife={0};
		long[] initialTime={0},arrivalTime={0};Vec3d[] arrival={null};
		context.runAtEveryTick(()->{
			try{
				context.assertTrue(++callbacks[0]<150,"ordinary ragdoll portal fixture admission/continuation deadline");
				if(stage[0]==0){
					if(!chunks.entityTicking(base,base.add(8,2,0)))return;
					var r=RagdollEntity.spawnScout(world,Vec3d.ofCenter(base),new Vec3d(.04,.01,-.02));actor[0]=r;
					context.assertTrue(r!=null&&world.getEntity(r.getUuid())==r,"existing articulated identity registered in ordinary world");
					r.applyPhysicsImpulse(r.getPos().add(.3,.1,0),new Vec3d(.4,.2,.1));
					initialAge[0]=r.age;initialTime[0]=world.getTime();stage[0]=1;return;
				}
				var r=actor[0];
				if(stage[0]==1){
					if(r.age-initialAge[0]<6)return;
					context.assertTrue(world.getTime()-initialTime[0]>=6,"six ordinary source world ticks elapsed");
					var identity=r.getUuid();var rig=r.rig();var simulation=r.simulation();var before=save(r);
					var positions=simulation.parts().stream().map(p->p.body().position()).toList();
					var rotations=simulation.parts().stream().map(p->p.body().rotation()).toList();
					var velocities=simulation.parts().stream().map(p->p.body().linearVelocity()).toList();
					var momenta=simulation.parts().stream().map(p->p.body().angularMomentum()).toList();
					var oldPrevious=new Vec3(r.prevX,r.prevY,r.prevZ);
					var previousParts=r.previousRenderParts();var currentParts=r.currentRenderParts();
					var source=pure(r.getPos());var destination=source.plus(new Vec3(8,2,0));
					var transform=D6PortalTransform.of(source,new Vec3(1,0,0),destination,new Vec3(0,-1,0));
					r.applyPhysicsTorque(new Vec3d(.03,-.02,.01));
					var force=simulation.parts().stream().map(p->p.body().accumulatedForce()).toList();
					var torque=simulation.parts().stream().map(p->p.body().accumulatedTorque()).toList();
					var sourceState=simulation.parts().stream().map(p->p.body().snapshot()).toList();
					var prepared=r.preparePortalTransfer(transform,new Vec3(.1,.02,0)).orElseThrow(()->new AssertionError("open complete destination must prepare"));
					for(int i=0;i<sourceState.size();i++)context.assertTrue(simulation.parts().get(i).body().matches(sourceState.get(i)),"preflight leaves every actual part untouched");
					context.assertTrue(save(r).equals(before),"preflight and torque queue do not change persistent pose/identity/lifetime");
					context.assertTrue(prepared.current()&&prepared.apply(),"accepted existing rig commits");
					var after=save(r);context.assertTrue(!prepared.apply()&&save(r).equals(after),"second application cannot transform twice");
					context.assertTrue(r.getUuid().equals(identity)&&world.getEntity(identity)==r&&r.simulation()==simulation&&r.rig()==rig,"no replacement entity, simulation, rig or joints");
					context.assertTrue(before.getInt("life")==after.getInt("life")&&r.age==initialAge[0]+6,"portal commit preserves TTL and normal age");
					near(context,destination,pure(r.getPos()),"entity COM follows the parts exactly once");
					near(context,transform.transformPoint(oldPrevious),new Vec3(r.prevX,r.prevY,r.prevZ),"previous root interpolation position shares the same map");
					for(int i=0;i<simulation.parts().size();i++){
						var b=simulation.parts().get(i).body();near(context,transform.transformPoint(positions.get(i)),b.position(),"every part moved");
						near(context,transform.transformVelocity(velocities.get(i)),b.linearVelocity(),"every part velocity rotated");
						near(context,transform.transformAngularMomentum(momenta.get(i)),b.angularMomentum(),"every part momentum rotated");
						near(context,transform.rotation().rotate(force.get(i)),b.accumulatedForce(),"pending force rotated without integration");
						near(context,transform.rotation().rotate(torque.get(i)),b.accumulatedTorque(),"pending torque rotated without integration");
						for(var axis:List.of(new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,0,1)))
							near(context,transform.rotation().rotate(rotations.get(i).rotate(axis)),b.rotation().rotate(axis),"all three authored axes rotated");
						near(context,transform.rotation().rotate(previousParts.get(i).position()),r.previousRenderParts().get(i).position(),"previous local interpolation offset rotated");
						near(context,transform.rotation().rotate(currentParts.get(i).position()),r.currentRenderParts().get(i).position(),"current local interpolation offset rotated");
					}
					near(context,r.simulation().centreVelocity().scaled(.05),pure(r.getVelocity()),"m/s converted once at entity boundary");
					arrival[0]=r.getPos();arrivalAge[0]=r.age;arrivalLife[0]=after.getInt("life");arrivalTime[0]=world.getTime();stage[0]=2;return;
				}
				if(r.age-arrivalAge[0]<6)return;
				context.assertTrue(world.getTime()-arrivalTime[0]>=6&&world.getEntity(r.getUuid())==r&&chunks.entityTicking(r.getBlockPos()),"same body continues on six actual destination world/entity ticks");
				context.assertTrue(r.getPos().distanceTo(arrival[0])<3,"ordinary solver stays at destination rather than snapping parts back to source");
				context.assertTrue(save(r).getInt("life")==arrivalLife[0]+r.age-arrivalAge[0],"life continues once per ordinary entity tick");
				r.discard();chunks.close();context.complete();
			}catch(RuntimeException|Error failure){if(actor[0]!=null)actor[0].discard();chunks.close();throw failure;}
		});
	}

	@GameTest(templateName=EMPTY_STRUCTURE,batchId="ragdoll-portal-refusal",tickLimit=180)
	public void rotatedLimbTerrainUnknownVolumeAndStaleTokenRefuseTheWholeTransfer(TestContext context){
		var world=context.getWorld();var base=context.getAbsolutePos(new BlockPos(7,80,7));
		var chunks=new AiFixtureChunks(world,base,1);RagdollEntity[] actor={null};int[] callbacks={0},initialAge={0};long[] start={0};
		context.runAtEveryTick(()->{
			try{
				context.assertTrue(++callbacks[0]<150,"ordinary refusal fixture deadline");
				if(actor[0]==null){
					if(!chunks.entityTicking(base,base.add(8,0,0)))return;
					actor[0]=RagdollEntity.spawnScout(world,Vec3d.ofCenter(base),Vec3d.ZERO);
					context.assertTrue(actor[0]!=null,"ordinary refusal body spawned");initialAge[0]=actor[0].age;start[0]=world.getTime();return;
				}
				var r=actor[0];if(r.age-initialAge[0]<2)return;
				context.assertTrue(world.getTime()-start[0]>=2&&world.getEntity(r.getUuid())==r,"refusal uses actual normally ticking body");
				var source=pure(r.getPos());var wall=base.add(8,1,0);var oldBlock=world.getBlockState(wall);
				var destination=new Vec3(wall.getX()+.5,wall.getY()-.52,wall.getZ()+.5);
				var turn=D6PortalTransform.of(source,new Vec3(1,0,0),destination,new Vec3(0,-1,0));
				var state=save(r);var snapshots=r.simulation().parts().stream().map(p->p.body().snapshot()).toList();boolean modified=r.velocityModified;
				world.setBlockState(wall,Blocks.OBSIDIAN.getDefaultState(),2);
				try{
					context.assertTrue(destination.y()+.22<wall.getY(),"core centre and core box are clear of wall");
					context.assertTrue(r.preparePortalTransfer(turn,new Vec3(0,0,0)).isEmpty(),"rotated spar crossing the wall refuses entire rig despite clear core");
				}finally{world.setBlockState(wall,oldBlock,2);}
				context.assertTrue(save(r).equals(state)&&r.velocityModified==modified,"blocked transfer changes no entity flags or persistent state");
				var unknown=new BlockPos(12_000_000,base.getY(),12_000_000);context.assertTrue(!world.isChunkLoaded(unknown),"unknown destination starts unloaded");
				context.assertTrue(r.preparePortalTransfer(D6PortalTransform.of(source,new Vec3(1,0,0),pure(Vec3d.ofCenter(unknown)),new Vec3(-1,0,0)),new Vec3(0,0,0)).isEmpty(),"UNKNOWN complete destination refuses without loading");
				context.assertTrue(!world.isChunkLoaded(unknown)&&save(r).equals(state),"preflight does not load or relocate anything");
				for(int i=0;i<snapshots.size();i++)context.assertTrue(r.simulation().parts().get(i).body().matches(snapshots.get(i)),"all refused raw part states stay exact");
				var prepared=r.preparePortalTransfer(turn,new Vec3(0,0,0)).orElseThrow(()->new AssertionError("cleared destination prepares"));
				world.setBlockState(wall,Blocks.OBSIDIAN.getDefaultState(),2);
				try{context.assertTrue(!prepared.current()&&!prepared.apply(),"a newly blocked limb invalidates cached clearance before mutation");}
				finally{world.setBlockState(wall,oldBlock,2);}
				context.assertTrue(prepared.current()&&save(r).equals(state),"cleared, unchanged world/body can still use unconsumed token");
				r.applyPhysicsTorque(new Vec3d(.01,.02,.03));var queued=r.simulation().parts().stream().map(p->p.body().snapshot()).toList();
				context.assertTrue(!prepared.current()&&!prepared.apply(),"late real torque invalidates prepared body before first limb moves");
				for(int i=0;i<queued.size();i++)context.assertTrue(r.simulation().parts().get(i).body().matches(queued.get(i)),"stale refusal preserves late input and every part");
				r.discard();chunks.close();context.complete();
			}catch(RuntimeException|Error failure){if(actor[0]!=null)actor[0].discard();chunks.close();throw failure;}
		});
	}

	private static NbtCompound save(RagdollEntity r){var tag=new NbtCompound();r.writeNbt(tag);return tag;}
	private static Vec3 pure(Vec3d v){return new Vec3(v.x,v.y,v.z);}
	private static void near(TestContext context,Vec3 expected,Vec3 actual,String message){context.assertTrue(expected.minus(actual).length()<1e-8,message+": "+expected+" != "+actual);}
}
