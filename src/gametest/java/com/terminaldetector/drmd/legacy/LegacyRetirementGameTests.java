package com.terminaldetector.drmd.legacy;

import com.terminaldetector.drmd.weapon.items.ModItems;
import com.terminaldetector.drmd.world.LegacyGeneration;
import com.terminaldetector.drmd.world.access.CubicAvailability;
import com.terminaldetector.drmd.world.end.EndReactorState;
import com.terminaldetector.drmd.world.level.WorldLevels;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.EndPortalFrameBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.boss.dragon.EnderDragonFight;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Registered only by legacy_retirement_focus.init.gradle with real tall registry data. */
public final class LegacyRetirementGameTests implements FabricGameTest {
    private static void fixture(TestContext c){
        var world=c.getWorld();var overworld=world.getServer().getOverworld();
        c.assertTrue(LegacyRetirementObservation.ENABLED,"explicit disposable legacy fixture required");
        c.assertTrue(LegacyGeneration.retired(),"automatic legacy generation is retired");
        c.assertTrue(overworld.getBottomY()==-784&&overworld.getHeight()==2672,
                "actual loaded tall-world registry required, got "+overworld.getBottomY()+"/"+overworld.getHeight());
        c.assertTrue(WorldLevels.isAdvancedColumn(world),"real old tall-world scope is active; no mocked height predicate");
        c.assertTrue(CubicAvailability.backend(world)==CubicAvailability.Backend.STOCK,"old column fixture is an actual stock world, not a native-cubic or snapshot substitute");
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="legacy-retirement-callbacks")
    public void realOldTallWorldFixtureAndRetiredFeatureControl(TestContext c){fixture(c);c.complete();}

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="legacy-retirement-callbacks")
    public void retiredEndEyeWithoutCatalystReachesVanilla(TestContext c){portal(c,false,false);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="legacy-retirement-callbacks")
    public void retiredEndEyeDoesNotConsumeLegacyCatalyst(TestContext c){portal(c,false,true);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="legacy-retirement-callbacks")
    public void retiredNetherIgnitionWithoutCatalystReachesVanilla(TestContext c){portal(c,true,false);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="legacy-retirement-callbacks")
    public void retiredNetherIgnitionDoesNotConsumeLegacyCatalyst(TestContext c){portal(c,true,true);}

    private static void portal(TestContext c,boolean nether,boolean catalyst){
        fixture(c);var world=c.getWorld();var player=c.createMockCreativeServerPlayerInWorld();
        try{
            player.changeGameMode(GameMode.SURVIVAL);player.getInventory().selectedSlot=0;
            player.getInventory().setStack(0,new ItemStack(nether?Items.FLINT_AND_STEEL:Items.ENDER_EYE,nether?1:2));
            var catalystItem=nether?ModItems.NETHER_GATE_CATALYST:ModItems.END_GATE_CATALYST;
            player.getInventory().setStack(1,catalyst?new ItemStack(catalystItem,3):ItemStack.EMPTY);
            var at=c.getAbsolutePos(new BlockPos(4,3,4));
            world.setBlockState(at,(nether?Blocks.OBSIDIAN:Blocks.END_PORTAL_FRAME).getDefaultState(),3);
            world.setBlockState(at.up(),Blocks.AIR.getDefaultState(),3);
            player.setPosition(Vec3d.ofCenter(at).add(0,1,1));
            var hit=new BlockHitResult(Vec3d.ofCenter(at).add(0,.5,0),Direction.UP,at,false);
            var result=UseBlockCallback.EVENT.invoker().interact(player,world,Hand.MAIN_HAND,hit);
            int remaining=player.getInventory().getStack(1).getCount();
            System.out.println("LEGACY_RETIREMENT_CALLBACK nether="+nether+" catalyst="+catalyst+" result="+result+" remaining="+remaining);
            c.assertTrue(result==ActionResult.PASS,"retired legacy callback must delegate ordinary item use, got "+result);
            c.assertTrue(remaining==(catalyst?3:0),"retired callback consumed a legacy catalyst: "+remaining);
            var vanilla=player.getMainHandStack().useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,hit));
            c.assertTrue(vanilla.isAccepted(),"normal vanilla item use should remain functional: "+vanilla);
            if(nether)c.assertTrue(world.getBlockState(at.up()).isOf(Blocks.FIRE),"normal fire starter lights the clear obsidian surface");
            else c.assertTrue(world.getBlockState(at).get(EndPortalFrameBlock.EYE)&&player.getMainHandStack().getCount()==1,"normal eye insertion occurs and consumes only the eye");
        }finally{player.discard();}
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="legacy-retirement-callbacks")
    public void unrelatedItemCallbackPreservesInventoryControl(TestContext c){
        fixture(c);var player=c.createMockCreativeServerPlayerInWorld();
        try{
            player.changeGameMode(GameMode.SURVIVAL);player.getInventory().selectedSlot=0;
            player.getInventory().setStack(0,new ItemStack(Items.STICK,2));player.getInventory().setStack(1,new ItemStack(ModItems.END_GATE_CATALYST,3));
            var at=c.getAbsolutePos(new BlockPos(4,3,4));c.getWorld().setBlockState(at,Blocks.STONE.getDefaultState(),3);
            var result=UseBlockCallback.EVENT.invoker().interact(player,c.getWorld(),Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(at),Direction.UP,at,false));
            c.assertTrue(result==ActionResult.PASS&&player.getInventory().getStack(0).getCount()==2&&player.getInventory().getStack(1).getCount()==3,"ordinary unrelated item callback stays inert");
        }finally{player.discard();}
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="legacy-retirement-dragon")
    public void retiredDragonTickNeverEntersAutomaticCitadelOrDiscardsDragon(TestContext c){
        fixture(c);var end=c.getWorld().getServer().getWorld(World.END);
        c.assertTrue(end!=null&&WorldLevels.isAdvancedColumn(end),"actual loaded End is scoped by the real old Overworld");
        var player=c.createMockCreativeServerPlayerInWorld();var dragon=EntityType.ENDER_DRAGON.create(end);
        c.assertTrue(dragon!=null,"actual vanilla dragon witness exists");
        try{
            player.teleport(end,8.5,70,8.5,Set.of(),0,0);
            dragon.refreshPositionAndAngles(12.5,80,12.5,0,0);end.spawnEntity(dragon);
            c.assertTrue(!EndReactorState.get(end).isBaseGenerated(),"fresh disposable world has no reactor arena");
            LegacyRetirementObservation.reset();
            var fight=new EnderDragonFight(end,0,new EnderDragonFight.Data(false,false,false,false,Optional.of(dragon.getUuid()),Optional.empty(),Optional.of(List.of())));
            fight.tick();
            System.out.println("LEGACY_RETIREMENT_DRAGON arenaEntries="+LegacyRetirementObservation.forbiddenArenaEntries+" dragonAlive="+dragon.isAlive()+" generated="+EndReactorState.get(end).isBaseGenerated());
            c.assertTrue(LegacyRetirementObservation.forbiddenArenaEntries==0,"retired dragon tick entered automatic Citadel generation "+LegacyRetirementObservation.forbiddenArenaEntries+" time(s); test sentinel prevented bulk writes");
            c.assertTrue(dragon.isAlive()&&!dragon.isRemoved()&&!EndReactorState.get(end).isBaseGenerated(),"retired tick preserves ordinary dragon and does not auto-build an arena");
        }finally{dragon.discard();player.discard();}
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="legacy-retirement-dragon")
    public void retiredRespawnRequestReachesActualVanillaBody(TestContext c){
        fixture(c);var end=c.getWorld().getServer().getWorld(World.END);
        c.assertTrue(end!=null,"actual loaded End required");
        LegacyRetirementObservation.reset();
        // dragonKilled=false makes the real vanilla request return without scanning/generating
        // an End portal. The field-read probe proves the mod's HEAD did not cancel it.
        var fight=new EnderDragonFight(end,0,new EnderDragonFight.Data(false,false,false,false,Optional.empty(),Optional.empty(),Optional.of(List.of())));
        fight.respawnDragon();
        System.out.println("LEGACY_RETIREMENT_RESPAWN vanillaBody="+LegacyRetirementObservation.vanillaRespawnEntries);
        c.assertTrue(LegacyRetirementObservation.vanillaRespawnEntries==1,"retired mod cancelled the actual vanilla respawn request before its normal field read");
        c.complete();
    }
}
