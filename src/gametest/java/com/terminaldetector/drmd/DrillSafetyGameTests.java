package com.terminaldetector.drmd;

import com.mojang.authlib.GameProfile;
import com.terminaldetector.drmd.pickup.LootField;
import com.terminaldetector.drmd.weapon.fx.WeaponFx;
import com.terminaldetector.drmd.world.engineer.HeavyDrillDrops;
import com.terminaldetector.drmd.world.micro.TunnelCarving;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.UUID;

public final class DrillSafetyGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void consecutiveHeavyCutsReuseOneStackAndRespectReservedLoot(TestContext context) {
        var world = context.getWorld();
        var at = context.getAbsolutePos(new BlockPos(4,5,4));
        var output = Vec3d.ofCenter(at);
        var reserved = new ItemEntity(world, output.x, output.y, output.z, new ItemStack(Items.COBBLESTONE));
        reserved.setOwner(UUID.randomUUID());
        var area = new net.minecraft.util.math.Box(at).expand(1);
        context.assertTrue(world.spawnEntity(reserved), "reserved vanilla item admitted");
        try {
            for (int pulse = 0; pulse < 12; pulse++) {
                world.setBlockState(at, Blocks.STONE.getDefaultState(), 2);
                try (var drops = new HeavyDrillDrops(world, null, output)) {
                    context.assertTrue(drops.breakBlock(at), "normal heavy cut");
                }
            }
            var items = world.getEntitiesByClass(ItemEntity.class, area, ItemEntity::isAlive);
            var newItems = items.stream().filter(item -> item != reserved).toList();
            context.assertTrue(newItems.size() == 1 && newItems.getFirst().getStack().getCount() == 12
                && reserved.getStack().getCount() == 1, "12 cuts yield one ordinary stack and preserve reserved ownership");
        } finally {
            world.getEntitiesByClass(ItemEntity.class, area, ItemEntity::isAlive).forEach(ItemEntity::discard);
        }
        context.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void denseFlightLootBoundsUpdatesAndIgnoresTheCornersOfTheQueryBox(TestContext context) {
        var world = context.getWorld();
        var center = Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(7,6,7)));
        var profile = new GameProfile(UUID.randomUUID(), "loot-perf");
        var player = new ServerPlayerEntity(world.getServer(), world, profile,
            ConnectedClientData.createDefault(profile, false).syncedOptions());
        player.setPosition(center);
        var data = DescentPlayerData.get(player);
        var items = new ArrayList<ItemEntity>();
        try {
            for (int n = 0; n < 512; n++) {
                var item = new ItemEntity(world, center.x + 3, center.y, center.z, new ItemStack(Items.COBBLESTONE), 0, 0, 0);
                context.assertTrue(world.spawnEntity(item), "loose item admitted");
                items.add(item);
            }
            var outside = new ItemEntity(world, center.x + 6, center.y, center.z + 6, new ItemStack(Items.COBBLESTONE), 0, 0, 0);
            context.assertTrue(world.spawnEntity(outside), "corner item admitted");
            var reserved = new ItemEntity(world, center.x + 1, center.y, center.z, new ItemStack(Items.COBBLESTONE), 0, 0, 0);
            reserved.setOwner(UUID.randomUUID());
            context.assertTrue(world.spawnEntity(reserved), "reserved item admitted");
            items.add(outside);
            items.add(reserved);
            LootField.tick(player, data);
            context.assertTrue(items.stream().noneMatch(item -> item.getVelocity().lengthSquared() > 0), "walk mode leaves the field off");
            data.setEnabled(true);
            LootField.tick(player, data);
            long moved = items.stream().filter(item -> item.getVelocity().lengthSquared() > 0).count();
            context.assertTrue(moved == LootField.MAX_ITEMS_PER_TICK && items.stream().allMatch(ItemEntity::isAlive)
                && outside.getVelocity().equals(Vec3d.ZERO) && reserved.getVelocity().equals(Vec3d.ZERO),
                "512 eligible drops have 64 updates; outer and reserved drops keep their native movement");
        } finally {
            items.forEach(ItemEntity::discard);
            DescentPlayerData.remove(player.getUuid());
            player.discard();
        }
        context.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void publicHeavyDropsAndBoundaryRingRejectUnknownAndKeepUnbreakableMaterial(TestContext context) {
        var world=context.getWorld();var at=context.getAbsolutePos(new BlockPos(4,5,4));
        var far=new BlockPos(((at.getX()+8_000_000)>>4)*16+8,at.getY(),((at.getZ())>>4)*16+8);
        context.assertTrue(!world.isChunkLoaded(far),"direct public mining target starts unknown");
        try(var drops=new com.terminaldetector.drmd.world.engineer.HeavyDrillDrops(world,null,Vec3d.ofCenter(at))) {
            context.assertTrue(!drops.breakBlock(far),"drop adapter refuses before unknown block/BE/loot queries");
        }
        context.assertTrue(TunnelCarving.carveBoundaryRing(world,far.getX(),far.getZ(),1.4,far.getY(),far.getY())==0
            &&!world.isChunkLoaded(far),"boundary authoring also retains unknown storage");
        world.setBlockState(at,Blocks.BEDROCK.getDefaultState(),2);
        try(var drops=new com.terminaldetector.drmd.world.engineer.HeavyDrillDrops(world,null,Vec3d.ofCenter(at))) {
            context.assertTrue(!drops.breakBlock(at)&&world.getBlockState(at).isOf(Blocks.BEDROCK),"direct helper cannot bypass hardness protection");
            world.setBlockState(at,Blocks.STONE.getDefaultState(),2);
            context.assertTrue(drops.breakBlock(at)&&world.getBlockState(at).isAir(),"known ordinary material still yields one normal break");
        }
        context.complete();
    }
    @GameTest(templateName="drmd:collapse_lab")
    public void heavyBoreBatchesLootAndStopsAddingEntitiesAtLocalCap(TestContext context) {
        var world=context.getWorld();var at=context.getAbsolutePos(new BlockPos(12,8,12));
        var area=new net.minecraft.util.math.Box(at).expand(12);
        world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,area,e->true).forEach(net.minecraft.entity.Entity::discard);
        for(var p:BlockPos.iterate(at.add(-3,-3,-3),at.add(3,3,3)))world.setBlockState(p,Blocks.STONE.getDefaultState(),2);
        int changed=TunnelCarving.carveCapsule(world,Vec3d.ofCenter(at),Vec3d.ofCenter(at).add(0,0,2),3,null,true);
        var items=world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,area,e->true);
        context.assertTrue(changed>20 && !items.isEmpty() && items.size()<=8,"large bore emits at most eight stack entities");
        context.assertTrue(items.stream().mapToInt(e->e.getStack().getCount()).sum()>items.size(),"resources are aggregated into stacks");
        items.forEach(net.minecraft.entity.Entity::discard);
        for(int i=0;i<32;i++)world.spawnEntity(new net.minecraft.entity.ItemEntity(world,at.getX()+.5,at.getY()+.5,at.getZ()+.5,
            new net.minecraft.item.ItemStack(net.minecraft.item.Items.COBBLESTONE)));
        for(var p:BlockPos.iterate(at.add(-3,-3,-3),at.add(3,3,3)))world.setBlockState(p,Blocks.STONE.getDefaultState(),2);
        context.assertTrue(TunnelCarving.carveCapsule(world,Vec3d.ofCenter(at),Vec3d.ofCenter(at).add(0,0,2),3,null,true)>0,"bore still advances at the item cap");
        context.assertTrue(world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,area,e->true).size()==32,"repeated heavy bore cannot grow local item population");
        world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,area,e->true).forEach(net.minecraft.entity.Entity::discard);
        context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void softenedStoneMakesProgressInsteadOfReturningCobblestoneForever(TestContext context) {
        var world=context.getWorld();var at=context.getAbsolutePos(new BlockPos(4,5,4));world.setBlockState(at,Blocks.STONE.getDefaultState(),2);
        context.assertTrue(WeaponFx.melt(world,at,2,null),"first material stage applied");
        context.assertTrue(WeaponFx.melt(world,at,2,null) && world.getBlockState(at).isAir(),"second stage actually removes material");context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void hotRockDoesNotCreateLavaAndProtectedBlocksOrContainersStayIntact(TestContext context) {
        var world=context.getWorld();var at=context.getAbsolutePos(new BlockPos(4,5,4));world.setBlockState(at,Blocks.NETHERRACK.getDefaultState(),2);
        WeaponFx.melt(world,at,2,null);context.assertTrue(world.getFluidState(at).isEmpty(),"mining does not create a lava source");
        world.setBlockState(at,Blocks.BEDROCK.getDefaultState(),2);context.assertTrue(!WeaponFx.melt(world,at,3,null) && world.getBlockState(at).isOf(Blocks.BEDROCK),"bedrock preserved");
        world.setBlockState(at,Blocks.CHEST.getDefaultState(),2);context.assertTrue(!WeaponFx.melt(world,at,3,null) && world.getBlockEntity(at)!=null,"container not replaced by a heat palette");context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void repeatingOneBoreDoesNotRewriteIdenticalBoundaryMasks(TestContext context) {
        var world=context.getWorld();var at=context.getAbsolutePos(new BlockPos(5,5,5));
        for(var p:BlockPos.iterate(at.add(-2,-2,-2),at.add(2,2,2)))world.setBlockState(p,Blocks.STONE.getDefaultState(),2);
        Vec3d middle=Vec3d.ofCenter(at);
        int first=TunnelCarving.carveCapsule(world,middle,middle.add(0,1,0),1.1,null);
        int second=TunnelCarving.carveCapsule(world,middle,middle.add(0,1,0),1.1,null);
        context.assertTrue(first>0 && second==0,"same bore only counts real geometry changes");context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void carvedFragmentsRemainMineableWhileContainersAreProtected(TestContext context) {
        var world=context.getWorld();var at=context.getAbsolutePos(new BlockPos(4,5,4));
        world.setBlockState(at,Blocks.STONE.getDefaultState(),2);
        var fragment=com.terminaldetector.drmd.world.micro.CarvedBlock.replace(world,at,Blocks.STONE.getDefaultState(),~1L);
        context.assertTrue(fragment!=null,"fixture uses the real persistent microblock");
        context.assertTrue(WeaponFx.melt(world,at,2,null) && world.getBlockState(at).isAir(),"container guard does not make an already carved wall unmineable");
        context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void remoteMiningCannotPullAnUnloadedChunkIntoMemory(TestContext context) {
        var world=context.getWorld();var far=new BlockPos(500000,80,500000);
        context.assertTrue(!world.isChunkLoaded(far),"fixture remote target starts unloaded");
        context.assertTrue(!WeaponFx.melt(world,far,3,null) && !world.isChunkLoaded(far),"guard runs before block-state reads");context.complete();
    }
}
