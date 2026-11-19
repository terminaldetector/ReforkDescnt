package com.terminaldetector.drmd;

import com.terminaldetector.drmd.entity.ModEntities;
import com.terminaldetector.drmd.entity.ProjectileEntity;
import com.terminaldetector.drmd.weapon.core.DamageClass;
import com.terminaldetector.drmd.world.contraption.BlockBodyEntity;
import com.terminaldetector.drmd.world.contraption.BodySpatialIndex;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GeneratedWorldDestructionGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="world-generated-oak-rocket",tickLimit=120)
    public void aRealRocketFellsAnOakPlacedByTheVanillaConfiguredFeature(TestContext c) {
        var world=c.getWorld();var root=c.getAbsolutePos(new BlockPos(9,160,9));
        var originals=new LinkedHashMap<BlockPos,BlockState>();
        var chunks=new AiFixtureChunks(world,root,1);
        c.runAtTick(12,()->{
            for(var p:BlockPos.iterate(root.add(-5,-1,-7),root.add(10,10,5))) {
                originals.put(p.toImmutable(),world.getBlockState(p));
                world.setBlockState(p,p.getY()==root.getY()-1?Blocks.DIRT.getDefaultState():Blocks.AIR.getDefaultState(),2);
            }
            var oak=world.getRegistryManager().get(RegistryKeys.CONFIGURED_FEATURE).get(Identifier.ofVanilla("oak"));
            c.assertTrue(oak!=null && oak.generate(world,world.getChunkManager().getChunkGenerator(),Random.create(42),root),
                "vanilla's actual oak feature generated an ordinary tree");
            c.assertTrue(world.getBlockState(root).isIn(BlockTags.LOGS),"generated ground-level trunk is the actual rocket target");
            rocket(c,root,95);
        });
        c.runAtTick(50,()->{
            try {
                var parts=bodies(world,root);
                c.assertTrue(parts.size()==1&&parts.getFirst().age>=8,"one generated tree was transferred to a normally ticking body");
                var tree=parts.getFirst();
                c.assertTrue(tree.cells().stream().anyMatch(b->b.state().isIn(BlockTags.LOGS))
                    &&tree.cells().stream().anyMatch(b->b.state().isIn(BlockTags.LEAVES)),"the moving tree retains its original wood and canopy");
                c.assertTrue(world.getBlockState(root).isAir()&&!world.getBlockState(root.up()).isIn(BlockTags.LOGS)
                    &&tree.physics().angularMomentum().length()>0,"real cut and tipping momentum leave no static duplicate trunk");
            } finally {clean(world,root,originals,chunks);}
            c.complete();
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="world-building-rocket-load",tickLimit=120)
    public void aStrongerHitCutsTheMasonrySupportAndDropsTheActualWoodenSlabs(TestContext c) {
        var world=c.getWorld();var root=c.getAbsolutePos(new BlockPos(9,160,9));
        var originals=new LinkedHashMap<BlockPos,BlockState>();
        var chunks=new AiFixtureChunks(world,root,1);
        c.runAtTick(12,()->{
            for(var p:BlockPos.iterate(root.add(-3,-6,-7),root.add(7,4,4))) {
                originals.put(p.toImmutable(),world.getBlockState(p));
                var state=p.getY()==root.getY()-6?Blocks.BEDROCK:Blocks.AIR;
                world.setBlockState(p,state.getDefaultState(),2);
            }
            for(int y=-5;y<=0;y++)world.setBlockState(root.up(y),Blocks.STONE_BRICKS.getDefaultState(),2);
            for(int x=0;x<=3;x++)world.setBlockState(root.up().east(x),Blocks.OAK_SLAB.getDefaultState(),2);
            rocket(c,root,12);
        });
        c.runAtTick(27,()->{
            c.assertTrue(world.getBlockState(root).isOf(Blocks.STONE_BRICKS)&&bodies(world,root).isEmpty(),
                "a light round leaves the masonry support and attached slab panel in place");
            rocket(c,root,95);
        });
        c.runAtTick(56,()->{
            try {
                var parts=bodies(world,root);
                c.assertTrue(parts.size()==1&&parts.getFirst().age>=8,"the detached panel is one normally ticking body");
                var panel=parts.getFirst();
                c.assertTrue(panel.cells().size()==4&&panel.cells().stream().allMatch(b->b.state().isOf(Blocks.OAK_SLAB))
                    &&panel.collider().boxes().stream().allMatch(box->box.max().y()-box.min().y()<=.500001),
                    "the four source slabs retain half-height colliders throughout the fall");
                c.assertTrue(world.getBlockState(root).isAir()&&world.getBlockState(root.up()).isAir()
                    &&panel.physicsCentre().y<root.getY()+.75,"the stronger shot cuts its support and gravity drops the real panel");
            } finally {clean(world,root,originals,chunks);}
            c.complete();
        });
    }

    private static void rocket(TestContext c,BlockPos target,int damage) {
        var world=c.getWorld();var shot=ModEntities.PROJECTILE.create(world);
        c.assertTrue(shot!=null,"actual projectile factory");
        shot.setDamageClass(DamageClass.EXPLOSIVE);shot.setDirectDamage(damage);shot.setSplashRadius(2);
        shot.setLifeTicks(30);shot.setMeshKind(ProjectileEntity.MESH_ROCKET);shot.setGravityStrength(0);shot.setDrag(0);
        shot.setPosition(Vec3d.ofCenter(target).subtract(0,0,5));shot.setVelocity(0,0,1);
        c.assertTrue(world.spawnEntity(shot),"ordinary rocket admission");
    }

    private static List<BlockBodyEntity> bodies(ServerWorld world,BlockPos root) {
        return BodySpatialIndex.query(world,new Box(root).expand(22)).stream().filter(BlockBodyEntity::physicsGravity).toList();
    }

    private static void clean(ServerWorld world,BlockPos root,Map<BlockPos,BlockState> originals,AiFixtureChunks chunks) {
        bodies(world,root).forEach(BlockBodyEntity::discard);
        originals.forEach((p,state)->world.setBlockState(p,state,2));
        chunks.close();
    }
}
