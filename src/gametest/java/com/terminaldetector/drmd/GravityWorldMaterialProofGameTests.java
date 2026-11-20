package com.terminaldetector.drmd;

import com.mojang.authlib.GameProfile;
import com.terminaldetector.drmd.physics.*;
import com.terminaldetector.drmd.weapon.items.ModItems;
import com.terminaldetector.drmd.world.access.WorldAccess;
import com.terminaldetector.drmd.world.contraption.*;
import com.terminaldetector.drmd.world.edit.WorldEditSafety;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.*;
import net.minecraft.test.*;
import net.minecraft.util.Hand;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import java.util.*;

/** Candidate existing-collapse->registered RAIL integration, not direct gun world-ripping. */
public final class GravityWorldMaterialProofGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="tree-rail-material",tickLimit=180)
    public void realTrunkCanopyCutThenRegisteredRailLaunchesIntoSolidTarget(TestContext c){drive(c,true,0);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="column-rail-material",tickLimit=180)
    public void finiteBuildingColumnCutThenRegisteredRailLaunchesWhileAnchoredCopyStays(TestContext c){drive(c,false,256);}
    private static void drive(TestContext c,boolean tree,int offset){
        var f=new Fixture(c,tree,offset);c.runAtEveryTick(()->{
            if(f.closed)return;
            try{f.tick();}catch(RuntimeException|Error bad){System.out.println("GRAV_WORLD_MATERIAL_FIRST_FAILURE="+f.state());f.close();throw bad;}
        });
    }
    private static final class Fixture {
        final TestContext c;final net.minecraft.server.world.ServerWorld w;final boolean tree;final BlockPos root,anchored;
        final AiFixtureChunks chunks;final Map<BlockPos,BlockState> originals=new LinkedHashMap<>();final Set<UUID> initialItems=new HashSet<>(),ownedItems=new HashSet<>();
        ServerPlayerEntity player;EmbeddedChannel channel;BlockBodyEntity body;int admission,phase;long capturedAt,launchedAt;BlockPos target;
        Vec3d launch;double furthest;boolean hit,closed;
        Fixture(TestContext c,boolean tree,int offset){this.c=c;w=c.getWorld();this.tree=tree;root=c.getAbsolutePos(new BlockPos(900000+offset,120,11000));anchored=root.east(8);chunks=new AiFixtureChunks(w,root,2);}
        String state(){return "tree="+tree+" phase="+phase+" worldTime="+w.getTime()+" held="+(player==null?-1:GravyPhysics.heldCount(player))+" body="+(body==null?"none":((net.minecraft.entity.Entity)body).getUuid()+" centre="+body.physicsCentre()+" v="+body.physicsVelocity());}
        List<ItemEntity> items(){var result=new ArrayList<ItemEntity>();w.collectEntitiesByType(TypeFilter.instanceOf(ItemEntity.class),new Box(root).expand(30),e->!e.isRemoved(),result,32);return result;}
        void put(BlockPos p,BlockState state){
            var before=WorldAccess.of(w).readIfLoaded(p).orElseThrow();c.assertTrue(!before.hasBlockEntity(),"fixture never replaces block entities");originals.putIfAbsent(p.toImmutable(),before);w.setBlockState(p,state,Block.NOTIFY_ALL);
        }
        void tick(){
            w.resetIdleTimeout();c.assertTrue(c.getTick()<170,"finite original180tick framework; no runtime budget extension");
            if(phase==0){
                c.assertTrue(PhysicsProfileState.expanded(w),"existing Expanded Physics profile required; fixture does not silently change save policy");
                if(!chunks.entityTicking(root,root.add(0,0,-18),root.add(0,10,24))||w.getTime()<8){c.assertTrue(++admission<80,"real ticking/loaded scene admission");return;}
                start();return;
            }
            if(phase==1&&w.getTime()-capturedAt>=8){
                c.assertTrue(body.age>=5&&GravyPhysics.heldCount(player)==1,"ordinary entity ticks keep the same held material body");
                launch=body.physicsCentre();target=BlockPos.ofFloored(launch.add(0,0,12));furthest=launch.z;
                for(int x=-5;x<=5;x++)for(int y=-6;y<=6;y++)put(target.add(x,y,0),Blocks.OBSIDIAN.getDefaultState());
                float before=DescentPlayerData.get(player).getGravyEnergy();
                c.assertTrue(ModItems.GRAVY_RAILGUN.use(w,player,Hand.MAIN_HAND).getResult().isAccepted(),"real second registered rail use launches; no direct fling service");
                c.assertTrue(!GravyPhysics.isHolding(player)&&Math.abs(before-DescentPlayerData.get(player).getGravyEnergy()-12)<.001,"one launch charge, actual release");
                c.assertTrue(body.physicsVelocity().z>40,"registered rail supplies its actual physical launch impulse");launchedAt=w.getTime();phase=2;return;
            }
            if(phase==2){
                Vec3d centre=body.physicsCentre();furthest=Math.max(furthest,centre.z);
                if(centre.z>target.getZ()-7&&centre.z<target.getZ()&&body.physicsVelocity().z<25)hit=true;
                if(w.getTime()-launchedAt<16)return;
                c.assertTrue(hit&&furthest<target.getZ()+.25&&((net.minecraft.entity.Entity)body).isAlive()&&w.getBlockState(target).isOf(Blocks.OBSIDIAN),"actual ordinary body/world contact stops real material at solid target: "+state()+" target="+target+" furthest="+furthest);
                c.assertTrue(w.getBlockState(anchored).isOf(Blocks.BRICKS)&&w.getBlockState(anchored.up(2).east()).isOf(Blocks.OBSIDIAN),"anchored building control remains unchanged");
                c.assertTrue(player.getMainHandStack().isOf(ModItems.GRAVY_RAILGUN)&&player.getMainHandStack().getCount()==1,"registered gun stack is not lost or duplicated");
                System.out.println("GRAV_WORLD_MATERIAL_PASS="+state()+" cells="+body.cells().size()+" actualTarget="+target+" materialSource=existing_support_cut_not_direct_gun_capture");close();c.complete();
            }
        }
        void start(){
            for(var p:BlockPos.iterate(root.add(-6,-3,-16),root.add(10,10,26))){var s=WorldAccess.of(w).readIfLoaded(p).orElseThrow();if(!s.isAir())put(p,Blocks.AIR.getDefaultState());}
            var profile=new GameProfile(UUID.randomUUID(),"grav-material");var data=ConnectedClientData.createDefault(profile,false);player=new ServerPlayerEntity(w.getServer(),w,profile,data.syncedOptions());
            var connection=new ClientConnection(NetworkSide.SERVERBOUND);channel=new EmbeddedChannel(connection);w.getServer().getPlayerManager().onPlayerConnect(connection,player,data);player.changeGameMode(GameMode.SURVIVAL);player.setNoGravity(true);player.getAbilities().flying=false;
            DescentPlayerData.get(player).setEnabled(false);DescentPlayerData.get(player).setGravyEnergy(100);player.getInventory().clear();player.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.GRAVY_RAILGUN));
            for(var e:items())initialItems.add(e.getUuid());
            for(int y=0;y<5;y++){put(root.up(y),(tree?Blocks.OAK_LOG:Blocks.BRICKS).getDefaultState());put(anchored.up(y),Blocks.BRICKS.getDefaultState());}
            if(tree)put(root.up(4).east(),Blocks.OAK_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT,false).with(LeavesBlock.DISTANCE,1));
            put(anchored.up(2).east(),Blocks.OBSIDIAN.getDefaultState());
            c.assertTrue(WorldEditSafety.mayChange(w,root,player)&&WorldEditSafety.mayChange(w,root.up(4),player),"actual ordinary Survival permissions before exposed collapse entry");
            c.assertTrue(StructureCollapse.rocketCut(w,anchored,Vec3d.ofCenter(anchored),new Vec3d(0,0,1),80)==null,"same existing support-cut refuses attached building before mutation");
            body=StructureCollapse.rocketCut(w,root,Vec3d.ofCenter(root),new Vec3d(0,0,1),80);
            c.assertTrue(body!=null&&body.cells().size()==(tree?5:4)&&body.physicsGravity(),"existing bounded collapse transfers actual remaining material once");
            if(tree)c.assertTrue(body.cells().stream().anyMatch(cell->cell.state().isOf(Blocks.OAK_LEAVES)),"actual canopy remains on tree body");
            for(int y=0;y<5;y++)c.assertTrue(w.getBlockState(root.up(y)).isAir(),"original root material is removed, not copied");
            long dropCount=0;for(var item:items())if(!initialItems.contains(item.getUuid())){ownedItems.add(item.getUuid());dropCount+=item.getStack().getCount();}c.assertTrue(dropCount==1,"only cut support gets normal loot, held material is not duplicated as items");
            var aim=body.physicsAimPoint();double eyeHeight=player.getEyeY()-player.getY();player.setPosition(aim.x,aim.y-eyeHeight,aim.z-10);player.setYaw(0);player.setPitch(0);player.setHeadYaw(0);
            c.assertTrue(Registries.ITEM.getId(ModItems.GRAVY_RAILGUN).toString().equals("drmd:weapon_d6_gravy_railgun"),"ordinary public registry item, not test subclass");
            c.assertTrue(ModItems.GRAVY_RAILGUN.use(w,player,Hand.MAIN_HAND).getResult().isAccepted(),"registered rail captures existing world-material body through normal item use");
            c.assertTrue(GravyPhysics.heldCount(player)==1&&Math.abs(GravyPhysics.heldMass(player)-body.physicsMass())<.001&&Math.abs(DescentPlayerData.get(player).getGravyEnergy()-84)<.001,"same finite material mass with one existing capture debit");
            capturedAt=w.getTime();phase=1;System.out.println("GRAV_WORLD_MATERIAL_CAPTURED="+state());
        }
        void close(){
            if(closed)return;closed=true;
            if(player!=null)GravyPhysics.release(player);if(body!=null)body.discard();
            for(var item:items())if(ownedItems.contains(item.getUuid()))item.discard();
            var entries=new ArrayList<>(originals.entrySet());Collections.reverse(entries);for(var e:entries)w.setBlockState(e.getKey(),e.getValue(),Block.NOTIFY_ALL);
            if(player!=null){var pm=w.getServer().getPlayerManager();if(pm.getPlayer(player.getUuid())==player)player.networkHandler.onDisconnected(new DisconnectionInfo(net.minecraft.text.Text.literal("gravity material proof closed")));player.discard();}
            if(channel!=null)channel.finishAndReleaseAll();chunks.close();
        }
    }
}
