package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.portal.PortalTransform.Quat;
import com.terminaldetector.drmd.client.portal.PortalTransform.Vec3;
import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.physics.collision.OrientedBox;
import com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions;
import com.terminaldetector.drmd.world.contraption.BlockBodyEntity;
import com.terminaldetector.drmd.world.contraption.BodySpatialIndex;
import com.terminaldetector.drmd.world.geometry.HybridMesh;
import com.terminaldetector.drmd.world.geometry.HybridTerrain;
import com.terminaldetector.drmd.world.geometry.NaturalGeometry;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.PillarBlock;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import java.util.List;

/** Paired near/body geometry and existing real-explosion extraction; no demonstration footage. */
public final class RoundedTreeGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="rounded-log-axis-pair")
    public void originalAxisSurvivesWorldMeshPhysicalColliderAndBodyNbt(TestContext c) {
        var world=c.getWorld();var at=c.getAbsolutePos(new BlockPos(3,24,3));
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)world.getChunk((at.getX()>>4)+x,(at.getZ()>>4)+z);
        var originals=new java.util.LinkedHashMap<BlockPos,net.minecraft.block.BlockState>();
        for(var p:BlockPos.iterate(at.add(-1,-1,-1),at.add(1,1,1))) {
            originals.put(p.toImmutable(),world.getBlockState(p));world.setBlockState(p,Blocks.AIR.getDefaultState(),2);
        }
        try {
            for(var axis:Direction.Axis.values()) {
                var state=Blocks.OAK_LOG.getDefaultState().with(PillarBlock.AXIS,axis);
                world.setBlockState(at,state,2);
                var expected=HybridMesh.LogAxis.valueOf(axis.name());
                var snapshot=HybridTerrain.snapshot(world,at,new HybridTerrain.Data(state,-1,true));
                c.assertTrue(snapshot.at(0,0,0).logAxis()==expected,"source AXIS reaches near geometry without changing state");
                var mesh=new HybridMesh(snapshot);var shape=state.getCollisionShape(world,at).getBoundingBox();
                c.assertTrue(Math.abs((axis==Direction.Axis.X?shape.minX:axis==Direction.Axis.Y?shape.minY:shape.minZ))<1e-9
                    &&Math.abs((axis==Direction.Axis.X?shape.maxX:axis==Direction.Axis.Y?shape.maxY:shape.maxZ)-1)<1e-9,
                    "rounded logs retain full source-axis length");
                var saved=net.minecraft.nbt.NbtHelper.fromBlockState(state);
                c.assertTrue(net.minecraft.nbt.NbtHelper.toBlockState(world.getRegistryManager().getWrapperOrThrow(RegistryKeys.BLOCK),saved).equals(state)
                    &&world.getBlockState(at).equals(state)&&world.getBlockEntity(at)==null,"world material/AXIS/palette identity survives");
                var rotation=ImmPtlQuaternions.rotationByDegrees(new Vec3(0,0,1),37);
                var cell=new BlockBodyEntity.Cell(BlockPos.ORIGIN,state);
                var body=BlockBodyEntity.createLocal(world,List.of(cell),Vec3d.of(at.east(8)),rotation,Vec3d.ZERO,Vec3d.ZERO);
                BlockBodyEntity copy=null;
                try {
                    for(int y=0;y<=4;y++)for(int z=0;z<=4;z++)for(int x=0;x<=4;x++)
                        c.assertTrue(mesh.vertex(x,y,z).equals(body.surface(cell).vertex(x,y,z)),"near and detached local mesh share all125 vertices");
                    Vec3d solid=axis==Direction.Axis.X?new Vec3d(.5,.13,.5):new Vec3d(.13,.5,.5);
                    Vec3d empty=axis==Direction.Axis.X?new Vec3d(.5,.13,.13):axis==Direction.Axis.Y?new Vec3d(.13,.5,.13):new Vec3d(.13,.13,.5);
                    c.assertTrue(!body.collisionQuery().contacts(probe(body.localToWorld(solid)),0).isEmpty(),"real curved face midpoint cannot disappear from the physical primitive");
                    c.assertTrue(body.collisionQuery().contacts(probe(body.localToWorld(empty)),0).isEmpty(),"rounded exterior corner is physically empty, not a hidden cube");
                    var tag=new NbtCompound();body.writeNbt(tag);copy=new BlockBodyEntity(ModEntities.BLOCK_BODY,world);copy.readNbt(tag);
                    c.assertTrue(copy.cells().equals(body.cells())&&NaturalGeometry.logAxis(copy.cells().getFirst().state())==expected
                        &&copy.surface(copy.cells().getFirst()).faces().equals(body.surface(cell).faces()),"local AXIS and original material survive actual body codec");
                    c.assertTrue(BodySpatialIndex.find(world,copy.getId()).isEmpty(),"unspawned saved clone cannot become a second live hull");
                }finally{body.discard();if(copy!=null)copy.discard();}
            }
        }finally{for(var e:originals.entrySet())world.setBlockState(e.getKey(),e.getValue(),2);}
        c.complete();
    }
    private static OrientedBox probe(Vec3d point) {
        return new OrientedBox(new Vec3(point.x,point.y,point.z),new Vec3(.002,.002,.002),Quat.IDENTITY);
    }
    @GameTest(templateName="drmd:collapse_lab",batchId="rounded-branch-explosion",tickLimit=160)
    public void existingVanillaExplosionExtractsConnectedMixedAxisBranchesAndPreservesTheirState(TestContext c) {
        var world=c.getWorld();var root=c.getAbsolutePos(new BlockPos(12,3,12));
        c.assertTrue(com.terminaldetector.drmd.physics.PhysicsProfileState.expanded(world),"real expanded profile admits existing tree physics");
        for(var p:BlockPos.iterate(root.add(-5,-1,-5),root.add(6,9,6)))world.setBlockState(p,Blocks.AIR.getDefaultState(),2);
        for(int y=0;y<6;y++)world.setBlockState(root.up(y),Blocks.OAK_LOG.getDefaultState(),2);
        for(int x=1;x<=3;x++)world.setBlockState(root.up(3).east(x),Blocks.OAK_LOG.getDefaultState().with(PillarBlock.AXIS,Direction.Axis.X),2);
        for(int z=1;z<=2;z++)world.setBlockState(root.up(4).south(z),Blocks.OAK_LOG.getDefaultState().with(PillarBlock.AXIS,Direction.Axis.Z),2);
        world.setBlockState(root.up(4).east(3),Blocks.OAK_LEAVES.getDefaultState(),2);
        world.setBlockState(root.up(4).south(3),Blocks.OAK_LEAVES.getDefaultState(),2);
        world.createExplosion(null,root.getX()+1.5,root.getY()+.5,root.getZ()+.5,4,net.minecraft.world.World.ExplosionSourceType.TNT);
        var bodies=BodySpatialIndex.query(world,new Box(root).expand(16)).stream().filter(b->b.physicsGravity()
            &&b.cells().stream().anyMatch(cell->NaturalGeometry.logAxis(cell.state())!=HybridMesh.LogAxis.NONE)).toList();
        c.assertTrue(bodies.size()==1,"existing real explosion hook extracts exactly one connected branched tree");
        var tree=bodies.getFirst();BlockBodyEntity copy=null;
        try {
            c.assertTrue(tree.cells().stream().filter(cell->NaturalGeometry.logAxis(cell.state())==HybridMesh.LogAxis.X).count()==3
                &&tree.cells().stream().filter(cell->NaturalGeometry.logAxis(cell.state())==HybridMesh.LogAxis.Z).count()==2,
                "horizontal branches are retained with their source axes instead of rejecting the whole tree");
            c.assertTrue(tree.cells().stream().filter(cell->cell.state().isIn(net.minecraft.registry.tag.BlockTags.LEAVES)).count()==2
                &&tree.cells().stream().allMatch(cell->tree.surface(cell)!=null),"connected canopy uses the same moving cached surface");
            c.assertTrue(world.getBlockState(root.up(3).east(2)).isAir()&&world.getBlockState(root.up(4).south()).isAir(),"one ownership transfer leaves no static branch duplicate");
            c.assertTrue(tree.physics().linearVelocity().length()>0&&tree.physics().angularMomentum().length()>0,"existing off-centre blast impulse supplies real tip momentum");
            var tag=new NbtCompound();tree.writeNbt(tag);copy=new BlockBodyEntity(ModEntities.BLOCK_BODY,world);copy.readNbt(tag);
            c.assertTrue(copy.physicsGravity()&&copy.cells().equals(tree.cells()),"gravity and mixed-axis structure survive body save without material substitution");
        }finally{tree.discard();if(copy!=null)copy.discard();}
        c.complete();
    }
}
