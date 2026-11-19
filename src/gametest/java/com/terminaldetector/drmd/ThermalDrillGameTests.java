package com.terminaldetector.drmd;

import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.world.micro.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.*;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

/** Registered-item callback + ordinary-world-clock coverage; native buttons/images and cold restart are separate. */
public final class ThermalDrillGameTests implements FabricGameTest {
    private static final AtomicInteger IDS=new AtomicInteger();
    private static final class Rig implements AutoCloseable {
        final TestContext c;final net.minecraft.server.world.ServerWorld w;final BlockPos at;final ServerPlayerEntity p;final AiFixtureChunks tickets;final io.netty.channel.embedded.EmbeddedChannel channel;boolean closed;
        Rig(TestContext c,boolean runtime){this(c,runtime,false);}
        Rig(TestContext c,boolean runtime,boolean survival){
            this.c=c;w=c.getWorld();at=new BlockPos(64008+IDS.getAndIncrement()*64,120,96008);
            if(runtime)tickets=new AiFixtureChunks(w,at,0);else{tickets=null;for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)w.getChunk((at.getX()>>4)+x,(at.getZ()>>4)+z);}
            if(survival){
                var data=net.minecraft.server.network.ConnectedClientData.createDefault(new com.mojang.authlib.GameProfile(UUID.randomUUID(),"thermal-loot"),false);
                p=new ServerPlayerEntity(w.getServer(),w,data.gameProfile(),data.syncedOptions());var connection=new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND);channel=new io.netty.channel.embedded.EmbeddedChannel(connection);
                w.getServer().getPlayerManager().onPlayerConnect(connection,p,data);p.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
            }else{p=c.createMockCreativeServerPlayerInWorld();channel=null;}
            DescentPlayerData.get(p).setEnabled(false);p.setNoGravity(true);p.setYaw(0);p.setPitch(0);
            p.setPosition(at.getX()+.5,at.getY()+.5-p.getEyeHeight(p.getPose()),at.getZ()-6);p.setVelocity(Vec3d.ZERO);
            p.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModWorldBlocks.TUNNEL_LASER));
        }
        void wall(BlockState material){for(var q:BlockPos.iterate(at.add(-3,-3,0),at.add(3,3,3)))w.setBlockState(q,material,2);}
        List<CarvedBlockEntity> carved(){var out=new ArrayList<CarvedBlockEntity>();for(var q:BlockPos.iterate(at.add(-4,-4,-3),at.add(4,4,7)))if(w.getBlockEntity(q) instanceof CarvedBlockEntity b)out.add(b);return out;}
        void item(int tick){var stack=p.getMainHandStack();if(tick==0)ModWorldBlocks.TUNNEL_LASER.use(w,p,Hand.MAIN_HAND);ModWorldBlocks.TUNNEL_LASER.usageTick(w,p,stack,72000-tick);}
        void pulse(BlockPos pos){ThermalDrillCut.pulse(w,pos,new Vec3d(0,0,1),p,2,1);}
        List<ItemEntity> items(){return w.getEntitiesByClass(ItemEntity.class,new Box(at).expand(18),ItemEntity::isAlive);}
        public void close(){if(closed)return;closed=true;try{items().forEach(ItemEntity::discard);if(channel!=null)w.getServer().getPlayerManager().remove(p);p.discard();}finally{if(channel!=null)channel.finishAndReleaseAll();if(tickets!=null)tickets.close();}}
    }
    private static void drive(Rig r,IntConsumer action){
        int[] age={-1},total={0};r.c.runAtEveryTick(()->{
            if(r.closed)return;
            try{
                r.c.assertTrue(++total[0]<240,"finite actual thermal fixture clock");
                if(age[0]<0&&r.tickets!=null&&!r.tickets.entityTicking(r.at))return;
                action.accept(++age[0]);
            }catch(RuntimeException|Error error){r.close();throw error;}
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="thermal-registered-item",tickLimit=260)
    public void registeredStrongItemHeatsBeforeOpeningAnActualCollisionClearCircularPassage(TestContext c){
        var r=new Rig(c,true);r.wall(Blocks.STONE.getDefaultState());
        drive(r,t->{
            if (t % 3 == 0) {
                // Keep ordinary aim fixed while the mock player's server head updates between pulses.
                r.p.setYaw(0);
                r.p.setHeadYaw(0);
                r.p.setPitch(0);
                var aim = com.terminaldetector.drmd.weapon.core.WeaponCore.aimDir(r.p);
                c.assertTrue(aim.squaredDistanceTo(new Vec3d(0, 0, 1)) < 1e-8,
                    "every accepted pulse uses the authored stationary forward aim: " + aim);
                r.item(t);
            }
            if(t==0){var hot=r.carved();c.assertTrue(!hot.isEmpty()&&hot.stream().allMatch(b->b.mask()==MicroGrid.FULL&&b.thermal()!=null&&b.thermal().heat()==1),"first real item callback heats intact material instead of instantaneous deletion");c.assertTrue(r.items().isEmpty(),"no loot before material removal");}
            if(t==120){
                for(int z=0;z<3;z++){
                    var p=Vec3d.ofCenter(r.at.add(0,0,z));var box=new Box(p.x-.3,p.y-.8,p.z-.3,p.x+.3,p.y+.8,p.z+.3);
                    for(var shape:r.w.getBlockCollisions(r.p,box))c.assertTrue(shape.isEmpty(),"actual player-size passage has no invisible whole-block wall");
                }
                c.assertTrue(r.carved().stream().anyMatch(b->b.mask()!=MicroGrid.FULL&&b.thermal()!=null&&b.thermal().bores().stream().anyMatch(q->q.radius()==2)),"requested full radius persists as real partial polygonal boundary");
                r.close();c.complete();
            }
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="thermal-material-time",tickLimit=260)
    public void actualSourceHardnessChangesTheHeatTimeWithoutInventingMaterialDrops(TestContext c){
        var r=new Rig(c,true);var iron=r.at.east(4);r.w.setBlockState(r.at,Blocks.STONE.getDefaultState(),2);r.w.setBlockState(iron,Blocks.IRON_BLOCK.getDefaultState(),2);int[] opened={-1,-1};
        drive(r,t->{
            if(t%3==0){r.pulse(r.at);r.pulse(iron);}
            if(opened[0]<0&&r.w.getBlockState(r.at).isAir())opened[0]=t;
            if(opened[1]<0&&r.w.getBlockState(iron).isAir())opened[1]=t;
            if(t==36){c.assertTrue(opened[0]>0&&opened[1]>opened[0],"iron genuinely requires more real pulses than stone: "+Arrays.toString(opened));r.close();c.complete();}
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="thermal-shared-work")
    public void repeatedSameTickCallbacksCannotBuyExtraHeatOrUnboundedWorldWrites(TestContext c){
        try(var r=new Rig(c,false)){
            r.wall(Blocks.STONE.getDefaultState());for(int i=0;i<10;i++)ThermalDrillCut.pulse(r.w,r.at,new Vec3d(0,0,1),r.p,3,5);
            var stats=ThermalDrillCut.stats(r.w);c.assertTrue(stats.writes()<=128&&stats.inspected()<=256&&stats.scanned()<=4096&&stats.emitters()<=32,"one bounded world budget across repeated callbacks: "+stats);
            c.assertTrue(r.carved().stream().allMatch(b->b.thermal()!=null&&b.thermal().dose()==1&&b.mask()==MicroGrid.FULL),"same-world-tick repetition supplies no extra heat");
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="thermal-permission-and-unknown")
    public void containersBedrockDisabledPermissionsAndUnknownVolumesStayIntact(TestContext c){
        try(var r=new Rig(c,false)){
            var chest=r.at.east();r.w.setBlockState(r.at,Blocks.BEDROCK.getDefaultState(),2);r.w.setBlockState(chest,Blocks.CHEST.getDefaultState(),2);r.pulse(r.at);
            c.assertTrue(r.w.getBlockState(r.at).isOf(Blocks.BEDROCK)&&r.w.getBlockState(chest).isOf(Blocks.CHEST),"protected material and real inventory are preserved");
            r.w.setBlockState(r.at,Blocks.STONE.getDefaultState(),2);r.p.getAbilities().allowModifyWorld=false;r.pulse(r.at);c.assertTrue(r.w.getBlockState(r.at).isOf(Blocks.STONE),"actual player permission is rechecked");
            var far=new BlockPos(500000,120,500000);c.assertTrue(!r.w.isChunkLoaded(far),"actual remote negative starts unavailable");
            ThermalDrillCut.pulse(r.w,far,new Vec3d(0,0,1),r.p,2,3);c.assertTrue(!r.w.isChunkLoaded(far),"no unavailable chunk materialization");
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="thermal-opaque-save")
    public void opaqueFutureAndMalformedHeatSurviveReadAndRefuseNewThermalAuthority(TestContext c){
        try(var r=new Rig(c,false)){
            var b=CarvedBlock.replace(r.w,r.at,Blocks.STONE.getDefaultState(),MicroGrid.FULL);var tag=b.createNbt(r.w.getRegistryManager());var future=new NbtCompound();future.putInt("schema",37);future.putString("futureOwner","untouched");tag.put("thermalDrill",future.copy());b.read(tag,r.w.getRegistryManager());r.pulse(r.at);
            c.assertTrue(b.thermalReadOnly()&&b.mask()==MicroGrid.FULL&&future.equals(b.createNbt(r.w.getRegistryManager()).get("thermalDrill")),"unknown raw compound remains exact and supplies no thermal authority");
            tag.put("thermalDrill",NbtString.of("malformed"));b.read(tag,r.w.getRegistryManager());r.pulse(r.at);
            c.assertTrue(b.thermalReadOnly()&&NbtString.of("malformed").equals(b.createNbt(r.w.getRegistryManager()).get("thermalDrill")),"corrupt raw type is retained/refused, not converted to fresh unclaimed heat");
            tag.remove("thermalDrill");b.read(tag,r.w.getRegistryManager());r.pulse(r.at);c.assertTrue(!b.thermalReadOnly()&&b.thermal()!=null&&b.source().isOf(Blocks.STONE),"genuine absent old metadata remains an ordinary mineable material");
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="thermal-cooling-clock",tickLimit=260)
    public void normalScheduledCoolingRestoresHeatOnlySolidsWithoutErasingOldDamage(TestContext c){
        var r=new Rig(c,true);var damaged=r.at.east(4);r.w.setBlockState(r.at,Blocks.STONE.getDefaultState(),2);CarvedBlock.replace(r.w,damaged,Blocks.STONE.getDefaultState(),~1L);
        drive(r,t->{
            if(t==0){r.pulse(r.at);r.pulse(damaged);c.assertTrue(r.w.getBlockEntity(r.at) instanceof CarvedBlockEntity,"actual initial heated solid exists");}
            if(t==45){
                c.assertTrue(r.w.getBlockState(r.at).isOf(Blocks.STONE),"ordinary world block ticks restore the intact source after heat expires");
                c.assertTrue(r.w.getBlockEntity(damaged) instanceof CarvedBlockEntity b&&b.mask()==~1L&&b.thermal()==null&&b.source().isOf(Blocks.STONE),"cooling removes only heat metadata, preserving prior physical damage");r.close();c.complete();
            }
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="thermal-codec-fields")
    public void partialCircularFieldAndHeatRoundTripThroughRealBlockEntityNbtWithoutAdvancingACut(TestContext c){
        try(var r=new Rig(c,false)){
            var bore=new ThermalBore(-1.5,-2,.5,-1.5,2,.5,2);var cell=new ThermalDrillCell(6,3,r.w.getTime(),r.w.getTime()+40,List.of(bore));var b=CarvedBlock.replace(r.w,r.at,Blocks.STONE.getDefaultState(),bore.mask());c.assertTrue(b!=null,"actual partially cut boundary");b.setThermal(cell);
            var saved=b.createNbt(r.w.getRegistryManager());var copy=new CarvedBlockEntity(r.at.east(8),ModWorldBlocks.CARVED.getDefaultState());copy.setWorld(r.w);copy.read(saved,r.w.getRegistryManager());
            c.assertTrue(saved.equals(copy.createNbt(r.w.getRegistryManager()))&&copy.mask()==b.mask()&&copy.thermal().equals(cell)&&copy.source().isOf(Blocks.STONE),"source/mask/field/heat restore exactly without a new pulse or removal");
            var data=copy.getRenderData();c.assertTrue(data.bores().equals(cell.bores())&&data.heat()==3&&data.organic(),"immutable client snapshot carries the same real field and thermal palette");
            var bad=saved.copy();bad.getCompound("thermalDrill").putString("newSchemaKey","preserve");copy.read(bad,r.w.getRegistryManager());c.assertTrue(copy.thermalReadOnly()&&bad.get("thermalDrill").equals(copy.createNbt(r.w.getRegistryManager()).get("thermalDrill")),"unsupported extra fields are preserved fail-closed");
        }c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="thermal-real-loot-cap",tickLimit=260)
    public void realThermalRemovalRetainsEightBirthsPerPulseAndThirtyTwoNearbyItems(TestContext c){
        var r=new Rig(c,true,true);r.wall(Blocks.STONE.getDefaultState());Set<UUID> seen=new HashSet<>();int[] removed={0};
        drive(r,t->{
            if(t==0)c.assertTrue(!r.p.isCreative()&&!r.p.isSpectator()&&r.p.interactionManager.getGameMode()==net.minecraft.world.GameMode.SURVIVAL&&r.w.getEntity(r.p.getUuid())==r.p&&r.p.getAbilities().allowModifyWorld&&r.w.canPlayerModifyAt(r.p,r.at)&&r.w.getGameRules().getBoolean(net.minecraft.world.GameRules.DO_TILE_DROPS),"PRECONDITION_UNOBSERVED: actual connected noncreative Survival principal, modification permission and tile drops");
            if(t%3==0){
                removed[0]+=ThermalDrillCut.pulse(r.w,r.at,new Vec3d(0,0,1),r.p,3,5);
                var current=r.items();long births=current.stream().filter(e->!seen.contains(e.getUuid())).count();c.assertTrue(births<=8&&current.size()<=32,"actual strong thermal path keeps8/32 caps, births="+births+" nearby="+current.size());current.forEach(e->seen.add(e.getUuid()));
            }
            if(t==90){c.assertTrue(removed[0]>20&&!seen.isEmpty(),"real heated material was removed and recovered, not disabled to satisfy RAM limits");r.close();c.complete();}
        });
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="thermal-real-fluid-and-opacity",tickLimit=260)
    public void intactHeatKeepsSourceOpacityAndRealWaterFlowsOnlyAfterTheMaterialOpens(TestContext c){
        var r=new Rig(c,true);var water=r.at.up();
        for(var q:BlockPos.iterate(r.at.add(-1,-1,-1),r.at.add(1,2,1)))r.w.setBlockState(q,Blocks.BEDROCK.getDefaultState(),2);
        r.w.setBlockState(r.at,Blocks.STONE.getDefaultState(),2);r.w.setBlockState(water,Blocks.WATER.getDefaultState(),3);
        drive(r,t->{
            if(t%3==0&&t<=18)r.pulse(r.at);
            if(t==0)c.assertTrue(r.w.getBlockState(r.at).getOpacity(r.w,r.at)==Blocks.STONE.getDefaultState().getOpacity(r.w,r.at)&&r.w.getFluidState(r.at).isEmpty(),"an intact heated face remains optically and physically closed");
            if(t==30){c.assertTrue(r.w.getFluidState(r.at).isIn(net.minecraft.registry.tag.FluidTags.WATER)&&r.w.getFluidState(r.at).get(net.minecraft.fluid.FlowableFluid.FALLING),"normal scheduled fluid updates fill the newly opened bounded cell downward from the real source");c.assertTrue(!r.w.getFluidState(r.at).isOf(net.minecraft.fluid.Fluids.LAVA),"thermal drilling never invents a lava source");r.close();c.complete();}
        });
    }
}
