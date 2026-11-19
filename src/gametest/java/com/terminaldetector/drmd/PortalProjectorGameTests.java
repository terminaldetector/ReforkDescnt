package com.terminaldetector.drmd;

import com.terminaldetector.drmd.entity.ModWorldBlocks;
import com.terminaldetector.drmd.world.portal.gun.*;
import com.terminaldetector.drmd.world.portal.mirror.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import java.util.*;

public final class PortalProjectorGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="portal-projector-gunaperturecannotteleportabodylargerthanitsvisibleopening")
    public void gunApertureCannotTeleportABodyLargerThanItsVisibleOpening(TestContext c) {
        var p=player(c);BlockPos a=at(c,8,8),b=at(c,24,8);var large=com.terminaldetector.drmd.entity.ModEntities.TRIPOD.create(c.getWorld());
        try {
            placed(c,p,1,wall(c,a,Direction.NORTH));placed(c,p,2,wall(c,b,Direction.EAST));
            large.setAiDisabled(true);large.setNoGravity(true);large.setPosition(Vec3d.ofCenter(a).add(0,0,-.3));c.getWorld().spawnEntity(large);
            large.prevX=large.getX();large.prevY=large.getY();large.prevZ=a.getZ()-.5;
            large.setPosition(large.getX(),large.getY(),a.getZ()+.2);large.setVelocity(0,0,1);Vec3d start=large.getPos();
            com.terminaldetector.drmd.world.portal.PortalTravel.carry(c.getWorld(),a,Direction.NORTH,b,Direction.EAST,2);
            c.assertTrue(large.getPos().equals(start) && large.getVelocity().equals(new Vec3d(0,0,1)),"oversized body cannot clip through gun opening");
        } finally {PortalProjector.clear(p);large.discard();p.discard();}
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="portal-projector-nativetravelcarriestheexistingmobframeandworldlooktogether")
    public void nativeTravelCarriesTheExistingMobFrameAndWorldLookTogether(TestContext c) {
        var p=player(c);BlockPos a=at(c,8,8),b=at(c,24,8);var cow=net.minecraft.entity.EntityType.COW.create(c.getWorld());
        try {
            placed(c,p,1,wall(c,a,Direction.NORTH));placed(c,p,2,wall(c,b,Direction.EAST));
            cow.setAiDisabled(true);cow.setNoGravity(true);cow.setPosition(Vec3d.ofCenter(a).add(0,0,-.2));c.getWorld().spawnEntity(cow);
            c.assertTrue(com.terminaldetector.drmd.world.gravity.ActorFrames.trySetUp(cow,new Vec3d(1,0,0)),"local-up mob frame initialized");
            cow.setYaw(0);cow.setPitch(-20);var beforeLook=cow.getRotationVector();
            cow.prevX=cow.getX();cow.prevY=cow.getY();cow.prevZ=a.getZ()-.6;cow.setVelocity(0,0,1);
            var src=new com.terminaldetector.drmd.client.portal.PortalTransform.Vec3(a.getX()+.5,a.getY()+.5,a.getZ());
            var dst=new com.terminaldetector.drmd.client.portal.PortalTransform.Vec3(b.getX()+1,b.getY()+.5,b.getZ()+.5);
            var turn=com.terminaldetector.drmd.d6.D6PortalTransform.of(src,new com.terminaldetector.drmd.client.portal.PortalTransform.Vec3(0,0,-1),dst,
                new com.terminaldetector.drmd.client.portal.PortalTransform.Vec3(1,0,0)).rotation();
            var wanted=turn.rotate(new com.terminaldetector.drmd.client.portal.PortalTransform.Vec3(beforeLook.x,beforeLook.y,beforeLook.z));
            // The current foot point must already have crossed the source plane.
            cow.setPosition(cow.getX(),cow.getY(),a.getZ()+.2);
            com.terminaldetector.drmd.world.portal.PortalTravel.carry(c.getWorld(),a,Direction.NORTH,b,Direction.EAST,2);
            c.assertTrue(cow.getX()>b.getX(),"mob transported");
            // Vanilla MathHelper trig is a 65536-entry lookup: inverse angles can differ by one table step.
            c.assertTrue(cow.getRotationVector().distanceTo(new Vec3d(wanted.x(),wanted.y(),wanted.z()))<2*Math.PI/65536,
                "world look is rotated once: before="+beforeLook+" expected="+wanted+" actual="+cow.getRotationVector()+" up="+com.terminaldetector.drmd.world.gravity.ActorFrames.up(cow)+" yaw="+cow.getYaw()+" pitch="+cow.getPitch()+" head="+cow.getHeadYaw());
            var up=turn.rotate(new com.terminaldetector.drmd.client.portal.PortalTransform.Vec3(1,0,0));
            c.assertTrue(com.terminaldetector.drmd.world.gravity.ActorFrames.up(cow).distanceTo(new Vec3d(up.x(),up.y(),up.z()))<1e-6,"physical up carried with the body");
        } finally {PortalProjector.clear(p);cow.discard();p.discard();}
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="portal-projector-genericworldeditscannotbypasstheexplicitpairadapter")
    public void genericWorldEditsCannotBypassTheExplicitPairAdapter(TestContext c) {
        var p=player(c);BlockPos a=at(c,8,8);
        try {
            placed(c,p,1,wall(c,a,Direction.NORTH));
            var access=com.terminaldetector.drmd.world.access.WorldAccess.of(c.getWorld());var before=access.snapshotIfLoaded(a).orElseThrow();
            var result=access.modifyServerAuthoritative(java.util.List.of(new com.terminaldetector.drmd.world.access.ServerBlockEdits.Edit(a,before,
                new com.terminaldetector.drmd.world.access.ServerBlockEdits.Cell(Blocks.AIR.getDefaultState(),null))));
            c.assertTrue(result.status()==com.terminaldetector.drmd.world.access.ServerBlockEdits.Status.UNSUPPORTED_BLOCK,"generic construction cannot move pair-owned BEs");
            c.assertTrue(c.getWorld().getBlockState(a).isOf(ModWorldBlocks.PORTAL_PANEL),"generic rejection has no partial write");
        } finally {PortalProjector.clear(p);p.discard();}
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="portal-projector-placementbeamusestheexistingtransformthroughanopenportal")
    public void placementBeamUsesTheExistingTransformThroughAnOpenPortal(TestContext c) {
        var p=player(c);BlockPos a=at(c,8,8),b=at(c,24,8),far=b.east(10);
        try {
            placed(c,p,1,wall(c,a,Direction.NORTH));placed(c,p,2,wall(c,b,Direction.EAST));
            var target=wall(c,far,Direction.WEST);
            // The beam resumes beyond a portal, outside the player's usual fixture room.
            // Load this explicit fixture corridor plus the collision-shape halo; do not rely
            // on chunks left by another test or on an earlier runGametest invocation.
            for(int x=(a.getX()>>4)-2;x<=(far.getX()>>4)+2;x++)
                for(int z=(a.getZ()>>4)-2;z<=(a.getZ()>>4)+2;z++)c.getWorld().getChunk(x,z);
            c.assertTrue(com.terminaldetector.drmd.world.edit.WorldEditSafety.loadedSegment(c.getWorld(),
                Vec3d.ofCenter(b).add(1,0,0),Vec3d.ofCenter(far)),"exit-side fixture corridor and shape halo loaded");
            var ray=PortalGunRay.trace(p,Vec3d.ofCenter(a).add(0,0,-3.5),new Vec3d(0,0,1));
            c.assertTrue(ray.hops()==1 && ray.hit()!=null && ray.hit().getBlockPos().equals(target.getBlockPos()),"beam reaches the wall beyond orange, not the blue backing: "+ray+" target="+target);
            c.assertTrue(ray.hit().getSide()==Direction.WEST,"destination surface normal retained");
        } finally {PortalProjector.clear(p);p.discard();}
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="portal-projector-linkedapertureallowsactualcollisionmovementandnativetravel")
    public void linkedApertureAllowsActualCollisionMovementAndNativeTravel(TestContext c) {
        var p=player(c);BlockPos a=at(c,8,8),b=at(c,24,8);var cow=net.minecraft.entity.EntityType.COW.create(c.getWorld());
        try {
            cow.setAiDisabled(true);cow.setNoGravity(true);
            Vec3d start=Vec3d.ofCenter(a).add(0,-.6,-1.3);cow.setPosition(start);c.getWorld().spawnEntity(cow);
            placed(c,p,1,wall(c,a,Direction.NORTH));
            cow.move(net.minecraft.entity.MovementType.SELF,new Vec3d(0,0,1.8));
            c.assertTrue(cow.getZ()<a.getZ(),"inactive face blocks ordinary movement");
            placed(c,p,2,wall(c,b,Direction.EAST));
            cow.setPosition(start);cow.prevX=start.x;cow.prevY=start.y;cow.prevZ=start.z;
            cow.setVelocity(0,0,1.3);cow.move(net.minecraft.entity.MovementType.SELF,new Vec3d(0,0,1.8));
            c.assertTrue(cow.getZ()>a.getZ(),"linked face can actually be entered");
            com.terminaldetector.drmd.world.portal.PortalTravel.carry(c.getWorld(),a,Direction.NORTH,b,Direction.EAST,PortalPanelBlock.HALF_SPAN);
            c.assertTrue(cow.getX()>b.getX()+1 && cow.getVelocity().x>1.2,"existing native engine transfers actual mover and momentum");
        } finally {PortalProjector.clear(p);cow.discard();p.discard();}
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="portal-projector-packetentryvalidatesheldgundirectionandcooldownbeforefiring")
    public void packetEntryValidatesHeldGunDirectionAndCooldownBeforeFiring(TestContext c) {
        var p=player(c);BlockPos a=at(c,8,8);
        try {
            wall(c,a,Direction.NORTH);
            double eye=p.getEyePos().y-p.getY();p.setPosition(Vec3d.ofCenter(a).add(0,-eye,-8));
            p.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,new net.minecraft.item.ItemStack(net.minecraft.item.Items.STICK));
            PortalProjectorItem.trigger(p,1,false,new Vec3d(0,0,1));
            c.assertTrue(PortalGunState.get(c.getWorld()).pair(p.getUuid()).blue()==null,"held-item spoof rejected");
            p.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,new net.minecraft.item.ItemStack(ModWorldBlocks.PORTAL_PROJECTOR));
            PortalProjectorItem.trigger(p,1,false,new Vec3d(Double.NaN,0,1));
            c.assertTrue(!p.getItemCooldownManager().isCoolingDown(ModWorldBlocks.PORTAL_PROJECTOR),"invalid aim cannot mutate cooldown or pair");
            PortalProjectorItem.trigger(p,1,false,new Vec3d(0,0,1));
            c.assertTrue(a.equals(PortalGunState.get(c.getWorld()).pair(p.getUuid()).blue()),"valid server ray places blue");
            PortalProjectorItem.trigger(p,2,true,new Vec3d(0,0,1));
            c.assertTrue(a.equals(PortalGunState.get(c.getWorld()).pair(p.getUuid()).blue()),"immediate repeated packet cannot bypass cooldown to clear");
        } finally {PortalProjector.clear(p);p.discard();}
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,batchId="portal-projector-other-face")
    public void differentOwnersCannotOverlapFacesBeyondTheirAnchorCells(TestContext c) {
        var p=player(c);var q=c.createMockCreativeServerPlayerInWorld();BlockPos a=at(c,8,8),b=a.east(3);
        try {
            var first=wall(c,a,Direction.NORTH);var second=wall(c,b,Direction.NORTH);
            placed(c,p,1,first);
            c.assertTrue(PortalProjector.place(q,1,second)==PortalProjector.Result.BLOCKED,"overlapping visible faces rejected even with separate empty anchor cells");
            c.assertTrue(PortalGunState.get(c.getWorld()).pair(q.getUuid()).blue()==null,"rejected owner has no partial pair");
            c.assertTrue(((PortalPanelBlockEntity)c.getWorld().getBlockEntity(a)).ownedBy(p.getUuid(),1),"existing owner is unchanged");
        } finally {PortalProjector.clear(p);PortalProjector.clear(q);q.discard();p.discard();}
        c.complete();
    }

    private ServerPlayerEntity player(TestContext c) {
        var world=c.getWorld();BlockPos at=c.getAbsolutePos(new BlockPos(8,80,8));
        for(int x=(at.getX()>>4)-2;x<=(at.getX()>>4)+2;x++)for(int z=(at.getZ()>>4)-2;z<=(at.getZ()>>4)+2;z++)world.getChunk(x,z);
        for(BlockPos pos:BlockPos.iterate(at.add(-5,-5,-5),at.add(25,5,25)))world.setBlockState(pos,Blocks.AIR.getDefaultState(),2);
        var p=c.createMockCreativeServerPlayerInWorld();p.setPosition(Vec3d.ofCenter(at.up(4)));return p;
    }
    private BlockHitResult wall(TestContext c,BlockPos anchor,Direction normal) {
        var axes=Arrays.stream(Direction.values()).filter(d->d.getAxis()!=normal.getAxis() && d.getDirection()==Direction.AxisDirection.POSITIVE).toArray(Direction[]::new);
        for(int a=-2;a<=2;a++)for(int b=-2;b<=2;b++) {
            BlockPos front=anchor.offset(axes[0],a).offset(axes[1],b);
            c.getWorld().setBlockState(front,Blocks.AIR.getDefaultState(),2);
            c.getWorld().setBlockState(front.offset(normal.getOpposite()),Blocks.STONE.getDefaultState(),2);
        }
        BlockPos back=anchor.offset(normal.getOpposite());return new BlockHitResult(Vec3d.ofCenter(back).add(Vec3d.of(normal.getVector()).multiply(.5)),normal,back,false);
    }
    private BlockPos at(TestContext c,int x,int z){return c.getAbsolutePos(new BlockPos(x,80,z));}
    private void placed(TestContext c,ServerPlayerEntity p,int slot,BlockHitResult hit) {
        c.assertTrue(PortalProjector.place(p,slot,hit)==PortalProjector.Result.PLACED,"projector placement succeeds");
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="portal-projector-twocolourslinkautomaticallywithoutfusionandkeepownership")
    public void twoColoursLinkAutomaticallyWithoutFusionAndKeepOwnership(TestContext c) {
        var p=player(c);BlockPos a=at(c,8,8),b=at(c,24,8);
        try {
            placed(c,p,1,wall(c,a,Direction.NORTH));
            c.assertTrue(!c.getWorld().getBlockState(a).get(PortalPanelBlock.LINKED),"one endpoint is inactive");
            placed(c,p,2,wall(c,b,Direction.EAST));
            var x=(PortalPanelBlockEntity)c.getWorld().getBlockEntity(a);var y=(PortalPanelBlockEntity)c.getWorld().getBlockEntity(b);
            c.assertTrue(x.ownedBy(p.getUuid(),1) && y.ownedBy(p.getUuid(),2),"owner/channel stored in actual BEs");
            c.assertTrue(x.isLinked() && y.isLinked() && b.equals(x.getLinkPartnerPos()) && a.equals(y.getLinkPartnerPos()),"both native links activate immediately");
            c.assertTrue(c.getWorld().getBlockState(a).get(PortalPanelBlock.CHANNEL)==1 && c.getWorld().getBlockState(b).get(PortalPanelBlock.CHANNEL)==2,"colours are blockstate data");
        } finally {PortalProjector.clear(p);p.discard();}
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="portal-projector-replacingonecolourmovesonlyitsownedendpointatomically")
    public void replacingOneColourMovesOnlyItsOwnedEndpointAtomically(TestContext c) {
        var p=player(c);BlockPos a=at(c,8,8),b=at(c,24,8),next=at(c,8,24);
        try {
            placed(c,p,1,wall(c,a,Direction.NORTH));placed(c,p,2,wall(c,b,Direction.SOUTH));
            placed(c,p,1,wall(c,next,Direction.WEST));
            c.assertTrue(c.getWorld().getBlockState(a).isAir(),"old blue anchor removed without drops");
            var other=(PortalPanelBlockEntity)c.getWorld().getBlockEntity(b);
            c.assertTrue(other.ownedBy(p.getUuid(),2) && next.equals(other.getLinkPartnerPos()),"orange retained and points at new blue");
            c.assertTrue(PortalGunState.get(c.getWorld()).pair(p.getUuid()).blue().equals(next),"persistent pair updated after commit");
        } finally {PortalProjector.clear(p);p.discard();}
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="portal-projector-invalidbackingandoccupiedfrontpreservetheexistingpair")
    public void invalidBackingAndOccupiedFrontPreserveTheExistingPair(TestContext c) {
        var p=player(c);BlockPos a=at(c,8,8),b=at(c,24,8),next=at(c,8,24);
        try {
            placed(c,p,1,wall(c,a,Direction.NORTH));placed(c,p,2,wall(c,b,Direction.SOUTH));
            var hit=wall(c,next,Direction.NORTH);c.getWorld().setBlockState(next.south().up(2),Blocks.AIR.getDefaultState(),2);
            c.assertTrue(PortalProjector.place(p,1,hit)==PortalProjector.Result.INVALID_SURFACE,"hole in backing rejected");
            wall(c,next,Direction.NORTH);c.getWorld().setBlockState(next.east(2),Blocks.CHEST.getDefaultState(),2);
            c.assertTrue(PortalProjector.place(p,1,hit)==PortalProjector.Result.BLOCKED,"occupied front never replaced");
            c.assertTrue(c.getWorld().getBlockState(next.east(2)).isOf(Blocks.CHEST),"container retained");
            c.assertTrue(c.getWorld().getBlockState(a).get(PortalPanelBlock.LINKED) && c.getWorld().getBlockState(b).get(PortalPanelBlock.LINKED),"failure leaves old pair linked");
        } finally {PortalProjector.clear(p);p.discard();}
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="portal-projector-supportsallsixsurfacenormalsandclearsbothends")
    public void supportsAllSixSurfaceNormalsAndClearsBothEnds(TestContext c) {
        var p=player(c);BlockPos a=at(c,8,8);
        try {
            for(Direction normal:Direction.values()) {
                placed(c,p,1,wall(c,a,normal));
                c.assertTrue(c.getWorld().getBlockState(a).get(PortalPanelBlock.FACING)==normal,"normal preserved "+normal);
                c.assertTrue(PortalProjector.clear(p)==PortalProjector.Result.CLEARED && c.getWorld().getBlockState(a).isAir(),"owned endpoint removed");
                // Remove this orientation's backing before testing the next axis.
                for(BlockPos pos:BlockPos.iterate(a.add(-3,-3,-3),a.add(3,3,3)))c.getWorld().setBlockState(pos,Blocks.AIR.getDefaultState(),2);
            }
        } finally {PortalProjector.clear(p);p.discard();}
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="portal-projector-differentplayerscannotrelinkorcleareachothersportals")
    public void differentPlayersCannotRelinkOrClearEachOthersPortals(TestContext c) {
        var p=player(c);var q=c.createMockCreativeServerPlayerInWorld();BlockPos a=at(c,8,8),b=at(c,24,8);
        try {
            c.assertTrue(!p.getUuid().equals(q.getUuid()),"independent test players");
            placed(c,p,1,wall(c,a,Direction.NORTH));placed(c,p,2,wall(c,b,Direction.SOUTH));
            c.assertTrue(PortalProjector.clear(q)==PortalProjector.Result.CLEARED,"empty foreign pair is harmless");
            c.assertTrue(!FusionPortalActivation.activate(c.getWorld(),a,Direction.NORTH,q),"Fusion cannot hijack gun-owned endpoints");
            c.assertTrue(((PortalPanelBlockEntity)c.getWorld().getBlockEntity(a)).ownedBy(p.getUuid(),1),"foreign ownership retained");
            p.getAbilities().allowModifyWorld=false;
            c.assertTrue(PortalProjector.clear(p)==PortalProjector.Result.DENIED,"permissions enforced on pair removal");
        } finally {p.getAbilities().allowModifyWorld=true;PortalProjector.clear(p);q.discard();p.discard();}
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="portal-projector-persistentpairandblockentitymetadataroundtripwithoutforeignentities")
    public void persistentPairAndBlockEntityMetadataRoundTripWithoutForeignEntities(TestContext c) {
        var p=player(c);BlockPos a=at(c,8,8),b=at(c,24,8);
        try {
            placed(c,p,1,wall(c,a,Direction.NORTH));placed(c,p,2,wall(c,b,Direction.SOUTH));
            var lookup=c.getWorld().getRegistryManager();var state=PortalGunState.get(c.getWorld());
            var copy=PortalGunState.read(state.writeNbt(new NbtCompound(),lookup),lookup);
            c.assertTrue(copy.pair(p.getUuid()).equals(state.pair(p.getUuid())),"pair saved by UUID and XYZ");
            var be=(PortalPanelBlockEntity)c.getWorld().getBlockEntity(a);var nbt=be.createNbtWithIdentifyingData(lookup);
            var loaded=new PortalPanelBlockEntity(a,c.getWorld().getBlockState(a));loaded.read(nbt,lookup);
            c.assertTrue(loaded.ownedBy(p.getUuid(),1) && loaded.isLinked() && b.equals(loaded.getLinkPartnerPos()),"client/save metadata complete");
            c.assertTrue(!nbt.containsUuid("attached"),"no Immersive Portals entity attached");
            c.getWorld().setBlockState(a,Blocks.AIR.getDefaultState(),3);
            c.assertTrue(!((PortalPanelBlockEntity)c.getWorld().getBlockEntity(b)).isLinked(),"ordinary block removal unlinks partner");
            c.assertTrue(state.pair(p.getUuid()).blue()==null,"ordinary removal updates saved pair index");
        } finally {PortalProjector.clear(p);p.discard();}
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="portal-projector-ungeneratedtargetisrejectedwithoutloadingorremovingoldportal")
    public void ungeneratedTargetIsRejectedWithoutLoadingOrRemovingOldPortal(TestContext c) {
        var p=player(c);BlockPos a=at(c,8,8);
        try {
            placed(c,p,1,wall(c,a,Direction.NORTH));
            BlockPos remote=a.add(500000,0,500000);
            c.assertTrue(!c.getWorld().isChunkLoaded(remote),"remote fixture is initially unloaded");
            var result=PortalProjector.place(p,1,new BlockHitResult(Vec3d.ofCenter(remote),Direction.NORTH,remote,false));
            c.assertTrue(result==PortalProjector.Result.UNAVAILABLE,"unknown cube is not empty terrain");
            c.assertTrue(!c.getWorld().isChunkLoaded(remote) && c.getWorld().getBlockState(a).isOf(ModWorldBlocks.PORTAL_PANEL),"no generation or old endpoint removal");
        } finally {PortalProjector.clear(p);p.discard();}
        c.complete();
    }
    /** Fresh projectors must be live world BEs, not merely linked map entries. No direct travel calls. */
    private final class FreshGunFixture implements AutoCloseable {
        final TestContext c;final BlockPos a,b;final AiFixtureChunks chunks;
        final Map<BlockPos,net.minecraft.block.BlockState> before=new LinkedHashMap<>();
        net.minecraft.server.network.ServerPlayerEntity player;io.netty.channel.embedded.EmbeddedChannel channel;
        boolean closed;int ticks,phase,age,steps,initialEpoch;Vec3d staged,arrived;
        FreshGunFixture(TestContext c){
            this.c=c;a=c.getAbsolutePos(new BlockPos(8,100,8));b=a.east(6);chunks=new AiFixtureChunks(c.getWorld(),a,1);
        }
        private void put(BlockPos pos,net.minecraft.block.BlockState state){
            var w=c.getWorld();var previous=com.terminaldetector.drmd.world.access.WorldAccess.of(w).readIfLoaded(pos).orElseThrow();
            c.assertTrue(!previous.hasBlockEntity(),"owned fixture never replaces a pre-existing BE");before.putIfAbsent(pos.toImmutable(),previous);
            w.setBlockState(pos,state,net.minecraft.block.Block.NOTIFY_ALL);
        }
        private void support(BlockPos at,Direction normal){
            var axes=Arrays.stream(Direction.values()).filter(d->d.getAxis()!=normal.getAxis()&&d.getDirection()==Direction.AxisDirection.POSITIVE).toArray(Direction[]::new);
            for(int u=-2;u<=2;u++)for(int v=-2;v<=2;v++){
                var front=at.offset(axes[0],u).offset(axes[1],v);put(front,Blocks.AIR.getDefaultState());put(front.offset(normal.getOpposite()),Blocks.STONE.getDefaultState());
            }
        }
        private void fire(int slot,BlockPos at,Direction normal){
            var hit=Vec3d.ofCenter(at.offset(normal.getOpposite())).add(Vec3d.of(normal.getVector()).multiply(.5));
            PortalProjectorItem.trigger(player,slot,false,hit.subtract(player.getEyePos()).normalize());
            c.assertTrue(at.equals(PortalGunState.get(c.getWorld()).pair(player.getUuid()).at(slot)),"registered held projector trigger creates actual colour "+slot);
        }
        void tick(){
            if(closed)return;var w=c.getWorld();w.resetIdleTimeout();c.assertTrue(++ticks<170,"fresh projector ticker/actual movement has finite170tick bound phase="+phase);
            if(!chunks.entityTicking(a,b))return;
            if(player==null){
                support(a,Direction.NORTH);support(b,Direction.EAST);
                var login=net.minecraft.server.network.ConnectedClientData.createDefault(new com.mojang.authlib.GameProfile(UUID.randomUUID(),"fresh-gun-user"),false);
                player=new net.minecraft.server.network.ServerPlayerEntity(w.getServer(),w,login.gameProfile(),login.syncedOptions());
                var connection=new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND);channel=new io.netty.channel.embedded.EmbeddedChannel(connection);
                w.getServer().getPlayerManager().onPlayerConnect(connection,player,login);
                player.changeGameMode(net.minecraft.world.GameMode.CREATIVE);player.getAbilities().flying=true;player.setNoGravity(true);DescentPlayerData.get(player).setEnabled(false);
                player.setPosition(Vec3d.ofCenter(a).add(0,-.6,-3.5));player.setVelocity(Vec3d.ZERO);player.setYaw(0);player.setPitch(0);
                player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,new net.minecraft.item.ItemStack(ModWorldBlocks.PORTAL_PROJECTOR));
                c.assertTrue(w.getServer().getPlayerManager().getPlayer(player.getUuid())==player&&w.getEntity(player.getUuid())==player,"traveller is an actual connected world player");
                initialEpoch=DescentPlayerData.get(player).portalEpoch();staged=player.getPos();fire(1,a,Direction.NORTH);phase=1;return;
            }
            // The embedded connection is absent from the server's socket tick list.
            player.networkHandler.tick();
            if(phase==1){
                c.assertTrue(!((PortalPanelBlockEntity)w.getBlockEntity(a)).isLinked(),"one genuine shot remains inactive");
                if(player.getItemCooldownManager().isCoolingDown(ModWorldBlocks.PORTAL_PROJECTOR))return;
                fire(2,b,Direction.EAST);
                for(var entry:Map.of(a,b,b,a).entrySet())c.assertTrue(w.getBlockEntity(entry.getKey()) instanceof PortalPanelBlockEntity panel&&panel.isLinked()&&entry.getValue().equals(panel.getLinkPartnerPos()),"actual quiet batch installed reciprocal pair");
                phase=2;age=0;return;
            }
            if(phase==2){
                c.assertTrue(player.getPos().distanceTo(staged)<1e-6&&DescentPlayerData.get(player).portalEpoch()==initialEpoch&&player.getYaw()==0&&player.getPitch()==0,"nearby linked pair cannot rotate or teleport a stationary connected player before contact");
                if(++age<5)return;phase=3;age=0;
            }
            if(phase==3){
                if(player.getX()>b.getX()+1){
                    c.assertTrue(steps>=2&&DescentPlayerData.get(player).portalEpoch()==initialEpoch+1,"normal BE tick/END-world drain, never direct carry, transfers actual inward movement once");
                    c.assertTrue(Math.abs(player.getYaw()+90)<.01&&Math.abs(player.getPitch())<.01,"fresh gun transfer rotates real world look exactly once");
                    arrived=player.getPos();phase=4;age=0;return;
                }
                c.assertTrue(++age<=20,"fresh linked gun pair must tick and transfer within20 real inward moves; map-only BEs cannot pass");
                steps++;player.setVelocity(0,0,.35);player.move(net.minecraft.entity.MovementType.SELF,new Vec3d(0,0,.35));player.setVelocity(Vec3d.ZERO);
                return;
            }
            if(phase==4){
                c.assertTrue(player.getPos().distanceTo(arrived)<.05&&DescentPlayerData.get(player).portalEpoch()==initialEpoch+1,"standing at actual exit never bounces or repeatedly turns after the10tick arrival guard");
                if(++age>=12){close();c.complete();}
            }
        }
        public void close(){
            if(closed)return;closed=true;var w=c.getWorld();
            try{
                if(player!=null){PortalProjector.clear(player);w.getServer().getPlayerManager().remove(player);player.discard();}
                for(var entry:before.entrySet())w.setBlockState(entry.getKey(),entry.getValue(),net.minecraft.block.Block.NOTIFY_ALL);
            }finally{if(channel!=null)channel.finishAndReleaseAll();chunks.close();}
        }
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="portal-projector-fresh-connected-ticker",tickLimit=200)
    public void freshHeldGunPairNaturallyTicksAndCarriesAConnectedPlayerExactlyOnce(TestContext c){
        var f=new FreshGunFixture(c);c.runAtEveryTick(()->{if(f.closed)return;try{f.tick();}catch(RuntimeException|Error failure){f.close();throw failure;}});
    }
}
