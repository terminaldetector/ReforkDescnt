package com.terminaldetector.drmd;
import com.terminaldetector.drmd.world.contraption.BlockBodyEntity;
import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import java.util.*;

public final class OrientedBodyGameTests implements FabricGameTest {
    private BlockPos arena(TestContext c) {
        BlockPos p=c.getAbsolutePos(new BlockPos(8,140,8));var w=c.getWorld();
        for(int x=(p.getX()>>4)-1;x<=(p.getX()>>4)+1;x++)for(int z=(p.getZ()>>4)-1;z<=(p.getZ()>>4)+1;z++)w.getChunk(x,z);
        for(var b:BlockPos.iterate(p.add(-4,-4,-4),p.add(6,6,6)))w.setBlockState(b,Blocks.AIR.getDefaultState(),2);
        return p;
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="obb-natural-support",tickLimit=300)
    public void smoothedTreeSupportUsesTheSameGeometryAsItsNarrowphase(TestContext c) {
        var p=arena(c).up(40);var w=c.getWorld();
        for(var cell:BlockPos.iterate(p.add(-10,-1,-10),p.add(10,10,10)))
            w.setBlockState(cell,cell.getY()==p.getY()-1?Blocks.OBSIDIAN.getDefaultState():Blocks.AIR.getDefaultState(),2);
        for(int y=0;y<=6;y++)w.setBlockState(p.up(y),Blocks.OAK_LOG.getDefaultState(),2);
        for(var cell:BlockPos.iterate(p.add(-2,4,-2),p.add(2,8,2)))
            if(w.getBlockState(cell).isAir() && Math.abs(cell.getX()-p.getX())+Math.abs(cell.getZ()-p.getZ())+Math.abs(cell.getY()-p.getY()-6)<=4)
                w.setBlockState(cell,Blocks.OAK_LEAVES.getDefaultState().with(net.minecraft.state.property.Properties.PERSISTENT,true),2);
        var b=com.terminaldetector.drmd.world.contraption.StructureCollapse.rocketCut(w,p,Vec3d.ofCenter(p),new Vec3d(0,0,-1),100);
        c.assertTrue(b!=null,"real support cut assembles the full smoothed tree");
        try {
            for(int i=0;i<300;i++)b.tick();
            c.assertTrue(Math.abs(b.rotation().x())>.35,"natural body physically tips instead of freezing on mismatched contact geometry: "+b.rotation());
            c.assertTrue(b.getY()>p.getY()-1,"support does not let the tree fall through its floor");
        } finally {b.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="obb-empty-corner")
    public void rotatedHullCanMovePastTheEmptyCornerOfItsWorldAabb(TestContext c) {
        var p=arena(c);var w=c.getWorld();w.setBlockState(p,Blocks.IRON_BLOCK.getDefaultState(),2);
        var b=BlockBodyEntity.assemble(w,p,p);
        try {
            b.physics().withRotation(new Quat(0,0,Math.sin(Math.PI/8),Math.cos(Math.PI/8))).withLinearVelocity(new Vec3(0,0,1));
            w.setBlockState(p.east().up(),Blocks.OBSIDIAN.getDefaultState(),2);
            double before=b.getZ();b.tick();
            c.assertTrue(b.getZ()>before+.049,"OBB passes empty diagonal corner instead of treating its broadphase as solid");
            c.assertTrue(b.physics().linearVelocity().z()>.99,"false corner contact does not erase momentum");
        } finally {b.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="obb-real-contact")
    public void actualOrientedOverlapStillBlocksTerrainAndPreservesTheWall(TestContext c) {
        var p=arena(c);var w=c.getWorld();w.setBlockState(p,Blocks.IRON_BLOCK.getDefaultState(),2);
        var b=BlockBodyEntity.assemble(w,p,p);
        try {
            b.physics().withRotation(new Quat(0,0,Math.sin(Math.PI/8),Math.cos(Math.PI/8))).withLinearVelocity(new Vec3(0,0,1));
            w.setBlockState(p.east(),Blocks.OBSIDIAN.getDefaultState(),2);var before=b.getPos();b.tick();
            c.assertTrue(b.getPos().distanceTo(before)<1e-9,"real oriented terrain contact stops motion");
            c.assertTrue(b.physics().linearVelocity().lengthSquared()<1e-9,"existing inelastic collision response retained");
            c.assertTrue(w.getBlockState(p.east()).isOf(Blocks.OBSIDIAN),"collision does not alter world terrain");
        } finally {b.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="obb-hollow-ray")
    public void mergedHullPreservesItsDoorwayAfterRotationAndNbtReload(TestContext c) {
        var p=arena(c);var w=c.getWorld();var selected=new LinkedHashMap<BlockPos,net.minecraft.block.BlockState>();
        for(int x=0;x<3;x++)for(int y=0;y<3;y++)if(x!=1||y!=1) {
            var cell=p.add(x,y,0);w.setBlockState(cell,Blocks.IRON_BLOCK.getDefaultState(),2);selected.put(cell,Blocks.IRON_BLOCK.getDefaultState());
        }
        var b=BlockBodyEntity.assembleSelection(w,selected);
        try {
            c.assertTrue(b.collider().boxes().size()<b.cells().size(),"solid cells share compound cuboids, not separate rigid bodies");
            b.physics().withRotation(new Quat(0,Math.sin(.35),0,Math.cos(.35)));
            var from=b.localToWorld(new Vec3d(1.5,1.5,-3));var to=b.localToWorld(new Vec3d(1.5,1.5,3));
            c.assertTrue(b.raycast(from,to).isEmpty(),"rotated doorway remains empty");
            var tag=new net.minecraft.nbt.NbtCompound();b.writeNbt(tag);
            var copy=new BlockBodyEntity(com.terminaldetector.drmd.entity.ModEntities.BLOCK_BODY,w);copy.readNbt(tag);
            try {
                c.assertTrue(copy.raycast(from,to).isEmpty(),"NBT reconstructs the same compound geometry");
                var hit=copy.raycastLocal(copy.localToWorld(new Vec3d(.5,.5,-3)),copy.localToWorld(new Vec3d(.5,.5,3))).orElseThrow();
                c.assertTrue(hit.normal().minus(new Vec3(0,0,-1)).length()<1e-9,"local ray reports the actual occupied face");
            } finally {copy.discard();}
        } finally {b.discard();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="obb-point-velocity")
    public void contactPointVelocityIncludesWorldAngularMotionAndGridComOffset(TestContext c) {
        var p=arena(c);var w=c.getWorld();w.setBlockState(p,Blocks.IRON_BLOCK.getDefaultState(),2);var b=BlockBodyEntity.assemble(w,p,p);
        try {
            b.physics().withLinearVelocity(new Vec3(3,2,1)).withAngularVelocity(new Vec3(0,0,2));
            c.assertTrue(b.surfaceVelocityAt(b.getPos().add(1,0,0)).distanceTo(new Vec3d(3,4,1))<1e-9,"surface velocity is v+omega cross radius in world units per second");
            var local=new Vec3d(.2,.3,.4);b.physics().withRotation(new Quat(0,0,Math.sin(.3),Math.cos(.3)));
            c.assertTrue(b.worldToLocal(b.localToWorld(local)).distanceTo(local)<1e-8,"grid/COM/world transforms are inverse");
        } finally {b.discard();}c.complete();
    }
}
