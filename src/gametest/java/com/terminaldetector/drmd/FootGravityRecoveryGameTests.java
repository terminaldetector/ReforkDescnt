package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.gravity.FootGravitySystem;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.UUID;

public final class FootGravityRecoveryGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void malformedLegacyGeneratorValuesCannotPoisonOrCrashTheFieldIndex(TestContext context) {
        var world=context.getWorld();var pos=context.getAbsolutePos(new BlockPos(2,4,2));
        world.setBlockState(pos,com.terminaldetector.drmd.entity.ModWorldBlocks.GRAVITY_GENERATOR.getDefaultState(),3);
        var generator=(com.terminaldetector.drmd.world.gravity.GravityGeneratorBlockEntity)world.getBlockEntity(pos);
        for(float bad:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,-1}) {
            var nbt=generator.createNbt(world.getRegistryManager());nbt.putFloat("radius",bad);nbt.putFloat("power",bad);
            generator.read(nbt,world.getRegistryManager());generator.registerField();
            context.assertTrue(Float.isFinite(generator.getRadius()) && generator.getRadius()>0,"radius repaired");
            context.assertTrue(Float.isFinite(generator.getPower()) && generator.getPower()>=0,"power repaired");
        }
        world.setBlockState(pos,net.minecraft.block.Blocks.AIR.getDefaultState(),3);context.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void finiteAuthoredGeneratorRadiusSurvivesTheIndexMigration(TestContext context) {
        var world=context.getWorld();var pos=context.getAbsolutePos(new BlockPos(2,4,2));
        world.setBlockState(pos,com.terminaldetector.drmd.entity.ModWorldBlocks.GRAVITY_GENERATOR.getDefaultState(),3);
        var generator=(com.terminaldetector.drmd.world.gravity.GravityGeneratorBlockEntity)world.getBlockEntity(pos);
        var nbt=generator.createNbt(world.getRegistryManager());nbt.putFloat("radius",512);nbt.putFloat("power",0);
        generator.read(nbt,world.getRegistryManager());generator.registerField();
        context.assertTrue(generator.getRadius()==512 && generator.getPower()==0,"valid authored values preserved");
        world.setBlockState(pos,net.minecraft.block.Blocks.AIR.getDefaultState(),3);context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void clientMirrorsCannotEraseOrReplaceServerGravity(TestContext context) {
        UUID id=UUID.randomUUID();
        FootGravitySystem.adoptServer(id,new Vec3d(1,0,0),"server wall");
        FootGravitySystem.adoptClient(id,new Vec3d(0,-1,0));
        context.assertTrue(FootGravitySystem.get(id).up().x>.99,"client ceiling did not replace server wall");
        FootGravitySystem.clearClient(id);
        context.assertTrue(FootGravitySystem.isActive(id),"late client off packet did not erase authoritative field");
        FootGravitySystem.forget(id); context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void bodyAndInteractionEyeUseTheSameWallFrameAndReturnUpright(TestContext context) {
        var player=context.createMockCreativeServerPlayerInWorld();
        player.setPosition(Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(5,6,5))));
        FootGravitySystem.adoptServer(player.getUuid(),new Vec3d(1,0,0),"wall");
        player.calculateDimensions();
        var wallBox=player.getBoundingBox();
        var eye=player.getEyePos().subtract(player.getPos());
        context.assertTrue(wallBox.getLengthX()>1.7 && wallBox.getLengthY()<.7,"actual collision body lies along local up");
        context.assertTrue(eye.x>1.5 && Math.abs(eye.y)<1e-6,"interaction eye is above the feet along the same wall");
        FootGravitySystem.clear(player.getUuid()); player.calculateDimensions();
        context.assertTrue(player.getBoundingBox().getLengthY()>1.7,"ordinary standing collision returned");
        context.assertTrue(player.getEyePos().y-player.getY()>1.5,"ordinary eye returned");
        FootGravitySystem.forget(player.getUuid()); player.discard(); context.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void leavingEveryRoomFaceRestoresAClearStandingBody(TestContext context) {
        var player=context.createMockCreativeServerPlayerInWorld();
        var world=context.getWorld();
        var centre=context.getAbsolutePos(new BlockPos(5,6,5));
        for(var pos:BlockPos.iterate(centre.add(-3,-3,-3),centre.add(3,3,3))) {
            boolean shell=Math.abs(pos.getX()-centre.getX())==3 || Math.abs(pos.getY()-centre.getY())==3 || Math.abs(pos.getZ()-centre.getZ())==3;
            world.setBlockState(pos,shell?net.minecraft.block.Blocks.IRON_BLOCK.getDefaultState():net.minecraft.block.Blocks.AIR.getDefaultState(),2);
        }
        Vec3d middle=Vec3d.ofCenter(centre);
        for(Vec3d up:new Vec3d[]{new Vec3d(1,0,0),new Vec3d(-1,0,0),new Vec3d(0,1,0),new Vec3d(0,-1,0),new Vec3d(0,0,1),new Vec3d(0,0,-1)}) {
            player.setPosition(middle.subtract(up.multiply(2.44)));
            FootGravitySystem.adoptServer(player.getUuid(),up,"room face");player.calculateDimensions();
            context.assertTrue(world.isSpaceEmpty(player,player.getBoundingBox()),"local standing body fits on room face "+up);
            com.terminaldetector.drmd.flight.FlightSystem.enableWithoutMount(player,DescentPlayerData.get(player));
            context.assertTrue(!FootGravitySystem.isActive(player) && world.isSpaceEmpty(player,player.getBoundingBox()),"H release gives upright clearance for "+up);
            context.assertTrue(player.getBoundingBox().getLengthY()>1.7,"standing height restored for "+up);
            com.terminaldetector.drmd.flight.FlightSystem.disable(player,DescentPlayerData.get(player));
        }
        FootGravitySystem.forget(player.getUuid());player.discard();context.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="torch-same-block-turn")
    public void turningAndRemovingARealTorchUpdatesItsOneStableFieldImmediately(TestContext c) {
        var world=c.getWorld();var pos=c.getAbsolutePos(new BlockPos(2,5,2));
        var torch=com.terminaldetector.drmd.entity.ModWorldBlocks.GRAVITY_TORCH;
        var original=world.getBlockState(pos);java.util.UUID identity=null;
        try {
            for(var facing:net.minecraft.util.math.Direction.values()) {
                world.setBlockState(pos,torch.getDefaultState().with(com.terminaldetector.drmd.world.gravity.GravityTorchBlock.FACING,facing),3);
                var field=com.terminaldetector.drmd.world.gravity.GravityFields.sample(world,Vec3d.ofCenter(pos));
                c.assertTrue(field!=null,"a real loaded native torch publishes immediately: "+facing);
                if(identity==null)identity=field.sourceId();
                c.assertTrue(identity.equals(field.sourceId())&&field.downDir().dotProduct(Vec3d.of(facing.getOpposite().getVector()))>.99999,
                    "same-block property changes retain one identity and expose the actual new local vector: "+facing);
                final var id=identity;
                c.assertTrue(com.terminaldetector.drmd.world.gravity.GravityFields.all().stream().filter(f->f.id().equals(id)).count()==1,
                    "scheduled/random republishing cannot duplicate one actual source");
            }
            world.setBlockState(pos,net.minecraft.block.Blocks.AIR.getDefaultState(),3);
            final var id=identity;
            c.assertTrue(com.terminaldetector.drmd.world.gravity.GravityFields.all().stream().noneMatch(f->f.id().equals(id)),
                "real block removal unregisters immediately before another tick/sample");
            c.assertTrue(com.terminaldetector.drmd.world.gravity.GravityFields.sample(world,Vec3d.ofCenter(pos))==null,
                "the removed source exerts no ghost gravity");
        } finally {world.setBlockState(pos,original,3);}
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="torch-dimension-isolation")
    public void equalCoordinateNativeTorchesHaveIndependentDimensionScopedLifecycles(TestContext c) {
        var a=c.getWorld();var b=a.getServer().getWorld(net.minecraft.world.World.NETHER);
        c.assertTrue(b!=null&&b!=a,"admission: actual second server dimension exists");
        var pos=c.getAbsolutePos(new BlockPos(3,5,3)).withY(64);
        a.getChunk(pos.getX()>>4,pos.getZ()>>4);b.getChunk(pos.getX()>>4,pos.getZ()>>4);
        var oldA=a.getBlockState(pos);var oldB=b.getBlockState(pos);
        var torch=com.terminaldetector.drmd.entity.ModWorldBlocks.GRAVITY_TORCH;
        try {
            a.setBlockState(pos,torch.getDefaultState().with(com.terminaldetector.drmd.world.gravity.GravityTorchBlock.FACING,net.minecraft.util.math.Direction.EAST),3);
            b.setBlockState(pos,torch.getDefaultState().with(com.terminaldetector.drmd.world.gravity.GravityTorchBlock.FACING,net.minecraft.util.math.Direction.NORTH),3);
            var first=com.terminaldetector.drmd.world.gravity.GravityFields.sample(a,Vec3d.ofCenter(pos));
            var second=com.terminaldetector.drmd.world.gravity.GravityFields.sample(b,Vec3d.ofCenter(pos));
            c.assertTrue(first!=null&&second!=null&&!first.sourceId().equals(second.sourceId()),"same coordinates own distinct actual dimension identities");
            c.assertTrue(first.downDir().x<-.99&&second.downDir().z>.99,"native world queries cannot blend another dimension");
            a.setBlockState(pos,net.minecraft.block.Blocks.AIR.getDefaultState(),3);
            var surviving=com.terminaldetector.drmd.world.gravity.GravityFields.sample(b,Vec3d.ofCenter(pos));
            c.assertTrue(com.terminaldetector.drmd.world.gravity.GravityFields.sample(a,Vec3d.ofCenter(pos))==null
                &&surviving!=null&&surviving.sourceId().equals(second.sourceId()),"removing one emitter cannot erase its equal-coordinate neighbour in another world");
        } finally {a.setBlockState(pos,oldA,3);b.setBlockState(pos,oldB,3);}
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="torch-native-transition-feedback")
    public void actualFootGravityEntryHoldAndReleaseEmitOnlyTransitionActionbars(TestContext c) {
        var world=c.getWorld();var pos=c.getAbsolutePos(new BlockPos(5,9,5));
        var original=world.getBlockState(pos);var profile=new com.mojang.authlib.GameProfile(UUID.randomUUID(),"gravity-notice");
        var options=net.minecraft.server.network.ConnectedClientData.createDefault(profile,false);
        var player=new net.minecraft.server.network.ServerPlayerEntity(world.getServer(),world,profile,options.syncedOptions()) {
            @Override public void sendMessage(net.minecraft.text.Text text,boolean overlay) {
                if(overlay)messages.add(text.getString());
            }
            final java.util.List<String> messages=new java.util.ArrayList<>();
        };
        var connection=new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND);
        var channel=new io.netty.channel.embedded.EmbeddedChannel(connection);
        world.getServer().getPlayerManager().onPlayerConnect(connection,player,options);
        player.messages.clear();
        DescentPlayerData.get(player).setEnabled(false);player.getAbilities().flying=false;
        player.setPosition(Vec3d.ofCenter(pos).add(0,1,0));
        try {
            world.setBlockState(pos,com.terminaldetector.drmd.entity.ModWorldBlocks.GRAVITY_TORCH.getDefaultState(),3);
            FootGravitySystem.tick(player);
            c.assertTrue(player.messages.size()==1&&FootGravitySystem.isActive(player),"actual native player entry emits one actionbar");
            for(int i=0;i<8;i++)FootGravitySystem.tick(player);
            c.assertTrue(player.messages.size()==1,"holding the same field is silent even when physics updates again");
            world.setBlockState(pos,net.minecraft.block.Blocks.AIR.getDefaultState(),3);
            FootGravitySystem.tick(player);
            c.assertTrue(player.messages.size()==2&&!FootGravitySystem.isActive(player),"actual upright release emits one exit actionbar and returns ownership");
            FootGravitySystem.tick(player);
            c.assertTrue(player.messages.size()==2,"ordinary world gravity cannot repeat an exit notice");
        } finally {
            FootGravitySystem.forget(player.getUuid());world.getServer().getPlayerManager().remove(player);
            player.discard();channel.finishAndReleaseAll();world.setBlockState(pos,original,3);
        }
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="foot-frame-malformed-recovery")
    public void malformedLaterFramesCannotPoisonActualPlayerBodyEyeOrRelease(TestContext c) {
        var player=c.createMockCreativeServerPlayerInWorld();var id=player.getUuid();
        player.setPosition(Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(5,30,5))));
        try {
            for(var direction:net.minecraft.util.math.Direction.values()) {
                var up=Vec3d.of(direction.getVector());FootGravitySystem.adoptServer(id,up,"valid frame");player.calculateDimensions();
                var body=player.getBoundingBox();var eye=player.getEyePos();
                for(var bad:new Vec3d[]{null,new Vec3d(Double.NaN,0,1),new Vec3d(0,Double.POSITIVE_INFINITY,1),new Vec3d(0,1,Double.NEGATIVE_INFINITY)}) {
                    com.terminaldetector.drmd.world.LocalOrientation.setUp(id,bad);
                    FootGravitySystem.adoptServer(id,bad,"invalid later frame");FootGravitySystem.adoptClient(id,bad);player.calculateDimensions();
                    c.assertTrue(FootGravitySystem.getUp(player).equals(up)&&player.getBoundingBox().equals(body)&&player.getEyePos().equals(eye),
                        "actual server body and interaction/camera eye retain the accepted frame after malformed input: "+direction);
                }
            }
            FootGravitySystem.adoptServer(id,new Vec3d(1e308,-1e308,5e307),"large finite legacy frame");player.calculateDimensions();
            var eye=player.getEyePos();var up=FootGravitySystem.getUp(player);
            var expectedEye=player.getPos().add(up.multiply(player.getEyeY()-player.getY()));
            // At millions of blocks, subtracting two world doubles quantizes the direction.
            // Verify the accepted unit basis, then the actual world-space physical eye exactly.
            c.assertTrue(Double.isFinite(eye.lengthSquared())&&up.distanceTo(new Vec3d(2,-2,1).normalize())<1e-15
                &&eye.squaredDistanceTo(expectedEye)<1e-20,
                "actual player eye accepts large finite input without overflow or a black-camera frame");
            FootGravitySystem.clear(id);com.terminaldetector.drmd.world.LocalOrientation.clear(id);player.calculateDimensions();
            c.assertTrue(!FootGravitySystem.isActive(player)&&player.getBoundingBox().getLengthY()>1.7&&player.getEyePos().y-player.getY()>1.5,
                "ordinary collision/eye release remains valid after rejected and normalized legacy updates");
        } finally {FootGravitySystem.forget(id);com.terminaldetector.drmd.world.LocalOrientation.clear(id);player.discard();}
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="foot-native-dimension-reset")
    public void actualDimensionExitRetiresOnlyTheTorchGravityFlagAndBodyFrame(TestContext c) {
        var source=c.getWorld();var destination=source.getServer().getWorld(net.minecraft.world.World.NETHER);
        c.assertTrue(destination!=null&&destination!=source,"admission: actual independent destination world exists");
        var at=c.getAbsolutePos(new BlockPos(5,20,5));var old=source.getBlockState(at);
        // This is an explicit disposable test destination, never a production load shortcut.
        destination.getChunk(at.getX()>>4,at.getZ()>>4);
        for(var pos:BlockPos.iterate(new BlockPos(at.getX()-2,94,at.getZ()-2),new BlockPos(at.getX()+2,100,at.getZ()+2)))destination.setBlockState(pos,net.minecraft.block.Blocks.AIR.getDefaultState(),2);
        try(var connection=gravityWalker(c,"gravity-transfer")) {
            var player=connection.player();DescentPlayerData.get(player).setEnabled(false);player.setNoGravity(false);player.getAbilities().flying=false;
            player.setPosition(Vec3d.ofCenter(at).add(0,1,0));
            source.setBlockState(at,com.terminaldetector.drmd.entity.ModWorldBlocks.GRAVITY_TORCH.getDefaultState(),3);
            FootGravitySystem.adoptAt(player,player.getPos());
            c.assertTrue(FootGravitySystem.isActive(player)&&player.hasNoGravity(),"real emitter acquired the actual native player's ordinary gravity flag");
            FootGravitySystem.tick(player);connection.channel().runPendingTasks();
            c.assertTrue(connection.channel().outboundMessages().stream().anyMatch(raw->raw instanceof net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket packet
                &&packet.payload() instanceof com.terminaldetector.drmd.network.ModNetworking.SyncPayload sync&&sync.footGravity()),"admission: actual transport announced the old active foot mirror");
            int announced=connection.channel().outboundMessages().size();
            player.teleport(destination,at.getX()+.5,96,at.getZ()+.5,0,0);
            connection.channel().runPendingTasks();
            c.assertTrue(connection.channel().outboundMessages().stream().skip(announced).anyMatch(raw->raw instanceof net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket packet
                &&packet.payload() instanceof com.terminaldetector.drmd.network.ModNetworking.SyncPayload sync&&!sync.footGravity()
                &&Math.abs(sync.localUx())<1e-6&&sync.localUy()>.99999&&Math.abs(sync.localUz())<1e-6),"actual world-change transport retires the foot camera/movement mirror with world up");
            c.assertTrue(player.getServerWorld()==destination&&!FootGravitySystem.isActive(player),"real native world transition invokes authoritative old-field cleanup");
            c.assertTrue(!player.hasNoGravity()&&player.getFinalGravity()>0,"dimension exit cannot retain an orphaned torch NoGravity flag");
            FootGravitySystem.tick(player);player.calculateDimensions();
            c.assertTrue(!FootGravitySystem.isActive(player)&&!player.hasNoGravity()&&player.getBoundingBox().getLengthY()>1.7
                &&player.getEyePos().y-player.getY()>1.5,"next actual foot update retains ordinary body, eye and world gravity");
        } finally {source.setBlockState(at,old,3);}
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="foot-gravity-flag-ownership")
    public void torchFlagReleasePreservesExternalFlagsAndARealFlightHandoff(TestContext c) {
        var world=c.getWorld();var at=c.getAbsolutePos(new BlockPos(5,20,5));var old=world.getBlockState(at);
        try(var connection=gravityWalker(c,"gravity-owner")) {
            var player=connection.player();var data=DescentPlayerData.get(player);data.setEnabled(false);player.getAbilities().flying=false;
            world.setBlockState(at,com.terminaldetector.drmd.entity.ModWorldBlocks.GRAVITY_TORCH.getDefaultState(),3);
            for(boolean original:new boolean[]{false,true}) {
                player.setPosition(Vec3d.ofCenter(at).add(0,1,0));player.setNoGravity(original);FootGravitySystem.adoptAt(player,player.getPos());
                c.assertTrue(player.hasNoGravity()&&FootGravitySystem.isActive(player),"actual native emitter owns foot motion before lifecycle clear");
                FootGravitySystem.forget(player.getUuid());
                c.assertTrue(player.hasNoGravity()==original&&!FootGravitySystem.isActive(player),"forget restores the actual original flag, including authored weightlessness");
            }
            player.setPosition(Vec3d.ofCenter(at).add(0,1,0));player.setNoGravity(true);FootGravitySystem.adoptAt(player,player.getPos());
            player.setNoGravity(false);FootGravitySystem.forget(player.getUuid());
            c.assertTrue(!player.hasNoGravity(),"an external false write wins over the old foot lease's captured true value");
            player.setPosition(Vec3d.ofCenter(at).add(0,1,0));FootGravitySystem.adoptAt(player,player.getPos());
            com.terminaldetector.drmd.flight.FlightSystem.enableWithoutMount(player,data);FootGravitySystem.forget(player.getUuid());
            c.assertTrue(data.isEnabled()&&player.hasNoGravity()&&!FootGravitySystem.isActive(player)&&!player.hasVehicle(),"real free-flight handoff retains the new owner without an orphan proxy or stale foot cleanup");
            com.terminaldetector.drmd.flight.FlightSystem.disable(player,data);
        } finally {world.setBlockState(at,old,3);}
        c.complete();
    }
    private record GravityWalker(net.minecraft.server.network.ServerPlayerEntity player,io.netty.channel.embedded.EmbeddedChannel channel) implements AutoCloseable {
        @Override public void close() {
            FootGravitySystem.forget(player.getUuid());com.terminaldetector.drmd.world.LocalOrientation.clear(player.getUuid());
            player.getServer().getPlayerManager().remove(player);player.discard();channel.finishAndReleaseAll();
        }
    }
    private static GravityWalker gravityWalker(TestContext c,String name) {
        var world=c.getWorld();var profile=new com.mojang.authlib.GameProfile(UUID.randomUUID(),name);
        var options=net.minecraft.server.network.ConnectedClientData.createDefault(profile,false);
        var player=new net.minecraft.server.network.ServerPlayerEntity(world.getServer(),world,profile,options.syncedOptions());
        var connection=new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND);
        var channel=new io.netty.channel.embedded.EmbeddedChannel(connection);
        world.getServer().getPlayerManager().onPlayerConnect(connection,player,options);
        return new GravityWalker(player,channel);
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="foot-open-ground-capture")
    public void aFloorTorchKeepsAnAirbornePlayerInPlaceThenLandsThroughNormalMovement(TestContext c) {
        var world=c.getWorld();var floor=c.getAbsolutePos(new BlockPos(7,144,7));
        var changed=new java.util.LinkedHashMap<BlockPos,net.minecraft.block.BlockState>();
        try(var lease=new AiFixtureChunks(world,floor,0);var connection=gravityWalker(c,"torch-fall")) {
            for(int x=-4;x<=4;x++)for(int z=-3;z<=3;z++){
                gravityBlock(c,changed,floor.add(x,x>1?1:0,z),net.minecraft.block.Blocks.STONE.getDefaultState());
                for(int y=2;y<=6;y++)gravityBlock(c,changed,floor.add(x,y,z),net.minecraft.block.Blocks.AIR.getDefaultState());
            }
            var torch=floor.add(0,1,2);
            gravityBlock(c,changed,torch,com.terminaldetector.drmd.entity.ModWorldBlocks.GRAVITY_TORCH.getDefaultState());
            var player=connection.player();
            player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
            DescentPlayerData.get(player).setEnabled(false);player.setNoGravity(false);player.getAbilities().flying=false;
            var start=new Vec3d(floor.getX()+.5,floor.getY()+4,floor.getZ()+.5);
            player.setPosition(start);player.setVelocity(0,-.12,0);
            FootGravitySystem.tick(player);
            c.assertTrue(FootGravitySystem.isActive(player)&&player.getPos().equals(start)
                &&player.getVelocity().y==-.12,"an open-air field preserves the airborne position and incoming momentum");
            double previous=start.y;
            for(int i=0;i<16;i++){
                FootGravitySystem.tick(player);
                FootGravitySystem.travel(player,Vec3d.ZERO);
                c.assertTrue(player.getY()>=floor.getY()+1-1e-6&&player.getY()<=previous+1e-6
                    &&previous-player.getY()<1.5&&world.isSpaceEmpty(player,player.getBoundingBox().contract(1e-6)),
                    "falling uses actual collision and stays above the real step terrain");
                previous=player.getY();
            }
            c.assertTrue(Math.abs(previous-(floor.getY()+1))<.09&&FootGravitySystem.get(player).grounded(),
                "the actual player settles on the ground without a mount snap");
            FootGravitySystem.travel(player,new Vec3d(0,1,0));double jumped=player.getY();
            FootGravitySystem.travel(player,Vec3d.ZERO);
            c.assertTrue(jumped>previous+.3&&player.getY()>jumped,"a local jump retains its upward velocity after takeoff");
        } finally {changed.forEach((pos,state)->world.setBlockState(pos,state,3));}
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="foot-blocked-gravity-turn")
    public void aWallTorchOnlyTurnsTheBodyWhenTheNewPhysicalBoxFits(TestContext c) {
        var world=c.getWorld();var floor=c.getAbsolutePos(new BlockPos(7,144,7));
        var changed=new java.util.LinkedHashMap<BlockPos,net.minecraft.block.BlockState>();
        try(var lease=new AiFixtureChunks(world,floor,0);var connection=gravityWalker(c,"torch-clearing")) {
            for(int x=-2;x<=3;x++)for(int z=-2;z<=3;z++){
                gravityBlock(c,changed,floor.add(x,0,z),net.minecraft.block.Blocks.STONE.getDefaultState());
                for(int y=1;y<=4;y++)gravityBlock(c,changed,floor.add(x,y,z),
                    (x==-1||x==1)?net.minecraft.block.Blocks.STONE.getDefaultState():net.minecraft.block.Blocks.AIR.getDefaultState());
            }
            var torch=floor.add(0,1,2);
            gravityBlock(c,changed,torch,com.terminaldetector.drmd.entity.ModWorldBlocks.GRAVITY_TORCH.getDefaultState()
                .with(com.terminaldetector.drmd.world.gravity.GravityTorchBlock.FACING,net.minecraft.util.math.Direction.EAST));
            var player=connection.player();
            player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
            DescentPlayerData.get(player).setEnabled(false);player.setNoGravity(false);player.getAbilities().flying=false;
            var start=new Vec3d(floor.getX()+.5,floor.getY()+1,floor.getZ()+.5);
            player.setPosition(start);player.setVelocity(Vec3d.ZERO);
            FootGravitySystem.tick(player);
            c.assertTrue(!FootGravitySystem.isActive(player)&&!player.hasNoGravity()&&player.getPos().equals(start)
                &&world.isSpaceEmpty(player,player.getBoundingBox().contract(1e-6)),
                "a physically blocked turn keeps the original clear body and force state");

            for(int z=-2;z<=3;z++)for(int y=1;y<=4;y++)
                gravityBlock(c,changed,floor.add(1,y,z),net.minecraft.block.Blocks.AIR.getDefaultState());
            FootGravitySystem.tick(player);
            c.assertTrue(FootGravitySystem.isActive(player)&&FootGravitySystem.getUp(player).x>.99999
                &&player.getPos().distanceTo(start)<.35&&world.isSpaceEmpty(player,player.getBoundingBox().contract(1e-6)),
                "a clear turn on open stepped terrain uses only the local support clearance");
            gravityBlock(c,changed,torch,net.minecraft.block.Blocks.AIR.getDefaultState());
            for(int i=0;i<30&&FootGravitySystem.isActive(player);i++){
                FootGravitySystem.tick(player);
                c.assertTrue(world.isSpaceEmpty(player,player.getBoundingBox().contract(1e-6)),
                    "the body remains clear while adopting world up from a live wall frame");
            }
            c.assertTrue(!FootGravitySystem.isActive(player)&&!player.hasNoGravity()
                &&player.getBoundingBox().getLengthY()>1.7,"ordinary gravity and upright geometry resume safely");
        } finally {changed.forEach((pos,state)->world.setBlockState(pos,state,3));}
        c.complete();
    }

    private static void gravityBlock(TestContext c,java.util.Map<BlockPos,net.minecraft.block.BlockState> changed,
                                     BlockPos pos,net.minecraft.block.BlockState state) {
        var old=c.getWorld().getBlockState(pos);
        c.assertTrue(!old.hasBlockEntity(),"open gravity fixture only replaces ordinary terrain");
        changed.putIfAbsent(pos.toImmutable(),old);
        c.getWorld().setBlockState(pos,state,3);
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="foot-native-save-ownership")
    public void nativePlayerSaveNeverPersistsTheTemporaryTorchGravityFlagOrLendsItsFrameToAnAlias(TestContext c) {
        var world=c.getWorld();var at=c.getAbsolutePos(new BlockPos(5,20,5));var old=world.getBlockState(at);
        try(var connection=gravityWalker(c,"gravity-save")) {
            var player=connection.player();DescentPlayerData.get(player).setEnabled(false);player.getAbilities().flying=false;
            player.setPosition(Vec3d.ofCenter(at).add(0,1,0));player.setNoGravity(false);
            world.setBlockState(at,com.terminaldetector.drmd.entity.ModWorldBlocks.GRAVITY_TORCH.getDefaultState().with(com.terminaldetector.drmd.world.gravity.GravityTorchBlock.FACING,net.minecraft.util.math.Direction.EAST),3);FootGravitySystem.adoptAt(player,player.getPos());player.calculateDimensions();
            c.assertTrue(player.hasNoGravity()&&FootGravitySystem.isActive(player)&&player.getBoundingBox().getLengthX()>1.7,"actual native torch holds the real player's sideways body before its ordinary native save");
            var saved=player.writeNbt(new net.minecraft.nbt.NbtCompound());
            c.assertTrue(!saved.getBoolean("NoGravity")&&player.hasNoGravity()&&FootGravitySystem.isActive(player),
                "native serialization records original world gravity without temporarily releasing the live owner");
            var profile=new com.mojang.authlib.GameProfile(player.getUuid(),"gravity-alias");
            var alias=new net.minecraft.server.network.ServerPlayerEntity(world.getServer(),world,profile,net.minecraft.server.network.ConnectedClientData.createDefault(profile,false).syncedOptions());
            try {
                alias.readNbt(saved);
                c.assertTrue(alias.getUuid().equals(player.getUuid())&&!alias.hasNoGravity()&&!FootGravitySystem.isActive(alias),
                    "a real native NBT decoder with saved UUID gets ordinary gravity and cannot borrow the live object's physical frame");
                alias.calculateDimensions();c.assertTrue(alias.getBoundingBox().getLengthY()>1.7,"decoded unowned player retains native upright geometry");
            } finally {alias.discard();}
            FootGravitySystem.forget(player.getUuid());
            c.assertTrue(!player.hasNoGravity(),"native save did not lose the original owner's lifecycle cleanup");
        } finally {world.setBlockState(at,old,3);}
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="foot-native-save-external-flags")
    public void nativePlayerSaveKeepsAuthoredWeightlessnessAndLaterExternalFalseWrites(TestContext c) {
        var world=c.getWorld();var at=c.getAbsolutePos(new BlockPos(5,20,5));var old=world.getBlockState(at);
        try(var connection=gravityWalker(c,"gravity-flag-save")) {
            var player=connection.player();DescentPlayerData.get(player).setEnabled(false);player.getAbilities().flying=false;
            player.setPosition(Vec3d.ofCenter(at).add(0,1,0));player.setNoGravity(true);
            world.setBlockState(at,com.terminaldetector.drmd.entity.ModWorldBlocks.GRAVITY_TORCH.getDefaultState(),3);FootGravitySystem.adoptAt(player,player.getPos());
            c.assertTrue(player.writeNbt(new net.minecraft.nbt.NbtCompound()).getBoolean("NoGravity"),"native serialization preserves weightlessness that existed before the field");
            player.setNoGravity(false);
            c.assertTrue(!player.writeNbt(new net.minecraft.nbt.NbtCompound()).getBoolean("NoGravity"),"native save preserves a later external false write instead of replaying an older captured true flag");
            FootGravitySystem.forget(player.getUuid());c.assertTrue(!player.hasNoGravity(),"subsequent actual clear also retains the external flag owner's last write");
        } finally {world.setBlockState(at,old,3);}
        c.complete();
    }
}
