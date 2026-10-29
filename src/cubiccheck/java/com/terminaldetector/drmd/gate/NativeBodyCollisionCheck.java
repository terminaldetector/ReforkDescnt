package com.terminaldetector.drmd.gate;

import com.terminaldetector.drmd.world.contraption.BlockBodyEntity;
import com.terminaldetector.drmd.world.contraption.BodyContactSystem;
import com.terminaldetector.drmd.world.contraption.BodySpatialIndex;
import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.d6.*;
import com.terminaldetector.drmd.physics.collision.*;
import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.mixin.client.CameraAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.*;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.math.*;
import java.nio.file.*;
import java.util.*;

/** Production BlockBody + native world volumes + actual renderer, in a disposable high-altitude arena. */
public final class NativeBodyCollisionCheck {
    private static final boolean FRACTURE=Boolean.getBoolean("drmd.gate.fracture");
    private static final int LAB_Y=Integer.getInteger("drmd.contacts.labY",2156);
    private static BlockPos lab(int x,int y,int z){return new BlockPos(x,LAB_Y+y-2156,z);}
    private static final BlockPos A=lab(8,2156,16),B=lab(16,2156,16),C=lab(8,2156,24),
        D=lab(18,2157,24),E=lab(16,2156,36),F=lab(8,2156,44),G=lab(18,2156,44),H=lab(8,2156,54),J=lab(5,2164,20),K=lab(-60,2176,28),L=lab(20,2164,45);
    private static final Set<Integer> DRAWN=new HashSet<>();
    private static final String[] BASE_NAMES={"diagonal-corner","terrain-stop","hollow-door","compound-contact-query","gravity-support","opposed-hull-impact","three-hull-chain","rotated-hull-impact"};
    private static final String[] NAMES=FRACTURE?java.util.stream.Stream.concat(Arrays.stream(BASE_NAMES),java.util.stream.Stream.of("fracture-halves","fast-fragments","saved-fragment-state")).toArray(String[]::new):BASE_NAMES;
    private static volatile BlockBodyEntity loadingHull;private static volatile boolean preloadProbe,setupInvoked;
    private static double minFastFragmentSpeed=Double.POSITIVE_INFINITY;
    private static int[] fastVisiblePixels={0,0};
    private static final List<String> RESULTS=new ArrayList<>();
    private static boolean started,preparing,finished;private static volatile boolean ready;
    private static int ticks,stage,frames;private static volatile int[] ids;
    private static int settledRotationFrames;
    private static Vec3[] pairPositions;private static Quat[] pairRotations;
    private static double maxPairClientError,maxQuantizedPoseError;private static int pairClientSamples,precisionWaitSamples,precisionWaitFrames;
    private static final Quat STOPPED_TURN=new Quat(.35,-.1,.2,.85).normalized();
    private static net.minecraft.entity.Entity entity(BlockBodyEntity b){return b;}
    public static void register(){ClientTickEvents.END_CLIENT_TICK.register(NativeBodyCollisionCheck::tick);}
    public static void drew(BlockBodyEntity b){if(ready && !finished)DRAWN.add(entity(b).getId());}
    /** Check the quaternion actually multiplied by the production renderer, not a second renderer. */
    public static void drawRotation(BlockBodyEntity b,Quat rendered) {
        if(!ready || finished || stage!=3 || frames<30 || entity(b).getId()!=ids[3])return;
        require(Math.abs(com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions.dot(b.rotation(),STOPPED_TURN))>1-1e-9,
            "post-spawn stopped rotation was not synchronized");
        require(Math.abs(com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions.dot(rendered.normalized(),STOPPED_TURN))>1-1e-9,
            "stationary hull replays its old rotation when partial tick wraps: "+rendered);
        settledRotationFrames++;
    }
    public static void camera(Camera camera) {
        if(!ready)return;BlockPos p=stage==0?A:stage==1?B.east():stage==2?C.add(1,1,0):stage==3?D:stage==4?E:stage==5?F:stage==6?G:stage==7?H:stage==8?J:stage==9?K.east(64):L;
        var a=(CameraAccessor)camera;a.drmd$invokeSetPos(Vec3d.ofCenter(p).add(stage==9?0:7,stage==9?12:4,stage==9?45:10));a.drmd$invokeSetRotation(stage==9?180:145,stage==9?15:18);
    }
    private static void tick(MinecraftClient client) {
        if(finished)return;
        if(++ticks>2400)throw new IllegalStateException("Native contact timeout stage="+stage+" ready="+ready+" started="+started+" preparing="+preparing+" loader="+(loadingHull!=null)+" setup="+setupInvoked);
        if(client.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen prompt)
            for(var child:prompt.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget button
                && button.getMessage().getString().equals("I know what I'm doing!"))button.onPress();
        if(ticks%400==0) {
            System.out.println("FRACTURE_UI screen="+(client.currentScreen==null?"null":client.currentScreen.getClass().getName()+" title="+client.currentScreen.getTitle().getString()+" widgets="+client.currentScreen.children().stream().filter(ch->ch instanceof net.minecraft.client.gui.widget.ButtonWidget).map(ch->((net.minecraft.client.gui.widget.ButtonWidget)ch).getMessage().getString()).toList())+" started="+started+" preparing="+preparing+" loader="+(loadingHull!=null)+" setup="+setupInvoked);
            if(ticks==400)try(var frame=ScreenshotRecorder.takeScreenshot(client.getFramebuffer())){frame.writeTo(client.runDirectory.toPath().resolve("fracture-debug-ui.png"));}catch(java.io.IOException e){throw new IllegalStateException(e);}
        }
        if(client.player==null || client.world==null || client.getServer()==null)return;
        com.terminaldetector.drmd.client.DescentClient.markUserFlightChoice();com.terminaldetector.drmd.client.DescentClientState.enabled=false;
        if(!started) {
            if(!Set.of("cubic-client-automation","hullcontactcheck","fracturecheck").contains(client.runDirectory.toPath().getFileName().toString()))throw new IllegalStateException("Disposable runtime required");
            started=true;client.setScreen(null);client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);client.options.hudHidden=true;
            DescentConfig.hud=false;DescentConfig.cockpit=false;DescentConfig.weaponView=false;DescentConfig.planetFloor=false;DescentConfig.levelSky=false;
            DescentConfig.portalSeeThrough=false;DescentConfig.mirrorReflection=false;
            if(FRACTURE){client.options.getCloudRenderMode().setValue(net.minecraft.client.option.CloudRenderMode.OFF);client.options.getFov().setValue(90);client.options.getFovEffectScale().setValue(0.0);}
            client.getServer().execute(()->{
                var p=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                com.terminaldetector.drmd.flight.FlightSystem.disable(p,com.terminaldetector.drmd.DescentPlayerData.get(p));
                p.getAbilities().allowFlying=true;p.getAbilities().flying=true;p.sendAbilitiesUpdate();
                p.teleport(p.getServerWorld(),14.5,LAB_Y,28.5,Set.of(),145,18);
            });return;
        }
        if(!ready && !preparing && client.player.getY()>LAB_Y-10 && loaded(client)) {
            preparing=true;client.getServer().execute(()->{
                if(FRACTURE)loadingHull=BlockBodyEntity.createLocal(client.getServer().getOverworld(),line(128),Vec3d.of(K));else setup(client);
            });
        }
        if(FRACTURE&&preparing&&!ready&&!setupInvoked&&loadingHull!=null&&!preloadProbe) {
            preloadProbe=true;client.getServer().execute(()->{try {
                var access=com.terminaldetector.drmd.world.access.WorldAccess.of(client.getServer().getOverworld());
                for(int x=-4;x<=4;x++)for(int y=(LAB_Y+18)>>4;y<=(LAB_Y+23)>>4;y++)
                    if(!access.isVolumeAvailable(new D6ChunkPos(x,y,1)))return;
                setupInvoked=true;setup(client);
            }finally{preloadProbe=false;}});
        }
    }
    private static boolean loaded(MinecraftClient c) {
        var access=com.terminaldetector.drmd.world.access.WorldAccess.of(c.world);
        for(int x=0;x<=1;x++)for(int y=(LAB_Y-4)>>4;y<=(LAB_Y+10)>>4;y++)for(int z=0;z<=3;z++)
            if(!access.isVolumeAvailable(new D6ChunkPos(x,y,z)))return false;
        return true;
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static void setup(MinecraftClient c) {
        var w=c.getServer().getOverworld();
        for(var p:BlockPos.iterate(lab(3,2152,10),lab(30,2166,60)))w.setBlockState(p,Blocks.AIR.getDefaultState(),2);
        w.setTimeOfDay(6000);w.getGameRules().get(net.minecraft.world.GameRules.DO_DAYLIGHT_CYCLE).set(false,c.getServer());
        var q=new Quat(0,0,Math.sin(Math.PI/8),Math.cos(Math.PI/8));
        w.setBlockState(A,Blocks.IRON_BLOCK.getDefaultState(),2);var a=BlockBodyEntity.assemble(w,A,A);
        a.physics().withRotation(q).withLinearVelocity(new Vec3(0,0,1));w.setBlockState(A.east().up(),Blocks.OBSIDIAN.getDefaultState(),2);
        double before=entity(a).getZ();entity(a).tick();require(entity(a).getZ()>before+.049,"native diagonal corner falsely blocks OBB");
        a.physics().withLinearVelocity(new Vec3(0,0,0));entity(a).tick();
        w.setBlockState(B,Blocks.IRON_BLOCK.getDefaultState(),2);var b=BlockBodyEntity.assemble(w,B,B);
        b.physics().withRotation(q).withLinearVelocity(new Vec3(8,0,0));w.setBlockState(B.east(2),Blocks.OBSIDIAN.getDefaultState(),2);
        for(int i=0;i<5;i++)entity(b).tick();
        require(entity(b).getX()>B.getX()+.7 && entity(b).getX()<B.getX()+1.3 && b.physics().linearVelocity().length()<1e-8,"native real wall contact missing");
        var cells=new LinkedHashMap<BlockPos,net.minecraft.block.BlockState>();
        for(int x=0;x<3;x++)for(int y=0;y<3;y++)if(x!=1||y!=1) {
            var p=C.add(x,y,0);w.setBlockState(p,Blocks.IRON_BLOCK.getDefaultState(),2);cells.put(p,Blocks.IRON_BLOCK.getDefaultState());
        }
        var hollow=BlockBodyEntity.assembleSelection(w,cells);hollow.physics().withRotation(new Quat(0,Math.sin(.35),0,Math.cos(.35)));entity(hollow).tick();
        require(hollow.raycast(hollow.localToWorld(new Vec3d(1.5,1.5,-4)),hollow.localToWorld(new Vec3d(1.5,1.5,4))).isEmpty(),"native doorway filled by broadphase");
        require(hollow.collider().boxes().size()<8,"native grid not merged");
        var access=com.terminaldetector.drmd.world.access.WorldAccess.of(w);Vec3 far=new Vec3(8.5,5000,16.5);var cube=new D6ChunkPos(0,5000>>4,1);
        require(!access.isVolumeAvailable(cube),"unknown exit fixture already available");var old=a.physics().position();
        var turn=D6PortalTransform.of(old,new Vec3(0,0,1),far,new Vec3(0,0,-1));
        require(!a.canCrossPortal(turn,far),"unknown native Y cube accepted");
        require(!access.isVolumeAvailable(cube) && a.physics().position().equals(old),"rejected query loads volume or mutates pose");
        var deckCells=new LinkedHashMap<BlockPos,net.minecraft.block.BlockState>();
        for(int x=0;x<4;x++)for(int z=0;z<4;z++) {
            var p=D.add(x,0,z);w.setBlockState(p,Blocks.IRON_BLOCK.getDefaultState(),2);deckCells.put(p,Blocks.IRON_BLOCK.getDefaultState());
        }
        var deck=BlockBodyEntity.assembleSelection(w,deckCells);
        var deckRotation=new Quat(.2,.3,.4,.7).normalized();deck.physics().withRotation(deckRotation);entity(deck).tick();
        var actorBox=new OrientedBox(pure(deck.localToWorld(new Vec3d(2,1.89,2))),new Vec3(.3,.9,.3),deckRotation);
        var contacts=deck.collisionQuery().contacts(actorBox,0);
        Vec3 normal=deckRotation.rotate(new Vec3(0,1,0));
        require(contacts.size()==1 && Math.abs(contacts.getFirst().penetration()-.01)<1e-8,"native compound resting contact missing");
        require(contacts.getFirst().normal().minus(normal).length()<1e-8,"native rotated contact normal incorrect");
        var fastActor=new OrientedBox(pure(deck.localToWorld(new Vec3d(2,4,2))),new Vec3(.3,.5,.3),deckRotation);
        var sweep=deck.collisionQuery().sweep(fastActor,normal.scaled(-1),normal.scaled(4)).orElseThrow();
        require(Math.abs(sweep.fraction()-.5)<1e-8,"native relative CCD missed moving support");
        Vec3 point=pure(deck.localToWorld(new Vec3d(2,1,2)));
        var actor=new D6PhysicsBody().withMass(1).withInertia(D6Mat3.diagonal(1,1,1))
            .withPosition(point.plus(normal.scaled(.9))).withLinearVelocity(normal.scaled(-2).plus(deckRotation.rotate(new Vec3(2,0,0))));
        Vec3 momentum=deck.physics().linearVelocity().scaled(deck.physicsMass()).plus(actor.linearVelocity());
        var reaction=new BodyContactConstraint(actor,deck.physics(),point,normal,.65);
        for(int i=0;i<32;i++)reaction.solve();
        require(deck.physics().linearVelocity().scaled(deck.physicsMass()).plus(actor.linearVelocity()).minus(momentum).length()<1e-8,"native two-sided contact loses momentum");
        require(Math.abs(reaction.relativeVelocity().dot(normal))<1e-7 && deck.physics().linearVelocity().dot(normal)<0,"native contact reaction missing");
        deck.physics().withLinearVelocity(new Vec3(0,0,0)).withAngularMomentum(new Vec3(0,0,0));entity(deck).tick();
        // Actual falling/tipping BlockBody exercises the changed terrain solver on native cubes.
        for(var p:BlockPos.iterate(E.add(-4,-1,-4),E.add(4,-1,4)))w.setBlockState(p,Blocks.OBSIDIAN.getDefaultState(),2);
        for(int y=0;y<6;y++)w.setBlockState(E.up(y),Blocks.IRON_BLOCK.getDefaultState(),2);
        var column=BlockBodyEntity.assemble(w,E,E.up(5));column.setPhysicsGravity(true);
        column.physics().withAngularVelocity(new Vec3(0,0,1));
        for(int i=0;i<240;i++)entity(column).tick();
        require(Math.abs(column.rotation().z())>.35,"native gravity support freezes the tipping column");
        require(entity(column).getY()>E.getY()-1,"native terrain support lets the column fall through");
        column.setPhysicsGravity(false);column.physics().withLinearVelocity(new Vec3(0,0,0)).withAngularMomentum(new Vec3(0,0,0));entity(column).tick();
        var unitCells=List.of(new BlockBodyEntity.Cell(BlockPos.ORIGIN,Blocks.IRON_BLOCK.getDefaultState()));
        var fastA=BlockBodyEntity.createLocal(w,unitCells,Vec3d.of(F));var fastB=BlockBodyEntity.createLocal(w,unitCells,Vec3d.of(F.east(3)));
        fastA.physics().withLinearVelocity(new Vec3(50,0,0));fastB.physics().withLinearVelocity(new Vec3(-50,0,0));
        entity(fastA).tick();entity(fastB).tick();require(entity(fastA).getX()>entity(fastB).getX(),"native fast fixture endpoints did not cross");
        require(BodyContactSystem.solve(w)>0&&!BodyContactSystem.stats(w).budgetPaused(),"native physical hull/hull CCD did not settle");
        require(entity(fastB).getX()-entity(fastA).getX()>=.999999&&fastA.physics().linearVelocity().length()<1e-6&&fastB.physics().linearVelocity().length()<1e-6,"native opposing hulls tunneled or lost their inelastic contact");
        var chain=new ArrayList<BlockBodyEntity>();for(int i=0;i<3;i++)chain.add(BlockBodyEntity.createLocal(w,unitCells,Vec3d.of(G).add(i*1.4,0,0)));
        chain.getFirst().physics().withLinearVelocity(new Vec3(50,0,0));for(var body:chain)entity(body).tick();
        require(BodyContactSystem.solve(w)>=2&&!BodyContactSystem.stats(w).budgetPaused(),"native changed trajectory did not reach third hull");
        for(int i=0;i<3;i++) {
            var body=chain.get(i);require(Math.abs(body.physics().linearVelocity().x()-50./3)<1e-4,"native three-body contact lost momentum");
            require(Math.abs(entity(body).getX()-(G.getX()+1.7333333333333334+i))<1e-4,"native collision discarded the rest of its tick");
            body.physics().withLinearVelocity(new Vec3(0,0,0));body.publishPhysicsState();
        }
        var offset=BlockBodyEntity.createLocal(w,List.of(unitCells.getFirst(),new BlockBodyEntity.Cell(BlockPos.ORIGIN.up(),Blocks.IRON_BLOCK.getDefaultState())),Vec3d.of(H));
        var orientation=new Quat(.2,.3,.4,.7).normalized();offset.physics().withRotation(orientation);entity(offset).tick();
        var target=offset.localToWorld(new Vec3d(1.53,1.1,.5));var striker=BlockBodyEntity.createLocal(w,unitCells,target.subtract(.5,.5,.5));
        striker.physics().withRotation(orientation).withLinearVelocity(orientation.rotate(new Vec3(-1,0,0)));
        Vec3 pairMomentum=offset.physics().linearVelocity().scaled(offset.physicsMass()).plus(striker.physics().linearVelocity().scaled(striker.physicsMass()));
        entity(offset).tick();entity(striker).tick();require(BodyContactSystem.solve(w)>0&&!BodyContactSystem.stats(w).budgetPaused(),"native rotated actual hull contact missing");
        require(offset.physics().angularVelocity().length()>1e-4&&striker.physics().angularVelocity().length()>1e-4,"native offset contact failed to spin both hulls");
        require(offset.physics().linearVelocity().scaled(offset.physicsMass()).plus(striker.physics().linearVelocity().scaled(striker.physicsMass())).minus(pairMomentum).length()<1e-7,"native rotated contact lost linear momentum");
        for(var body:List.of(offset,striker)){body.physics().withLinearVelocity(new Vec3(0,0,0)).withAngularMomentum(new Vec3(0,0,0));body.publishPhysicsState();}
        var pairBodies=new ArrayList<BlockBodyEntity>(List.of(fastA,fastB));pairBodies.addAll(chain);pairBodies.add(offset);pairBodies.add(striker);
        if(FRACTURE) {
            var split=fracture(c,BlockBodyEntity.createLocal(w,line(5),Vec3d.of(J)),5,2,new Quat(.2,.3,.4,.7).normalized(),new Vec3(1.5,-2,.3),new Vec3(.4,.2,-.1));
            freeze(split);pairBodies.addAll(split);
            var fast=fracture(c,loadingHull,128,64,Quat.IDENTITY,new Vec3(0,0,0),new Vec3(0,0,2));
            for(var part:fast) {
                var v=part.physics().linearVelocity();var position=part.massPosition();require(v.length()>60,"native fragment did not inherit its true tangential speed");
                minFastFragmentSpeed=Math.min(minFastFragmentSpeed,v.length());entity(part).tick();
                require(!part.boardingMotionPaused(),"native high-speed sample paused rather than integrating its loaded volume");
                require(part.physics().linearVelocity().minus(v).length()<1e-8&&part.massPosition().minus(position.plus(v.scaled(.05))).length()<1e-7,"native fragment momentum was clipped after its first whole tick");
            }
            freeze(fast);pairBodies.addAll(fast);
            var saved=fracture(c,BlockBodyEntity.createLocal(w,line(5),Vec3d.of(L)),5,2,new Quat(.2,.3,.4,.7).normalized(),new Vec3(2,1,0),new Vec3(.1,.2,.3));
            int indexed=BodySpatialIndex.loaded(w).size();
            for(var part:saved) {
                var clone=com.terminaldetector.drmd.entity.ModEntities.BLOCK_BODY.create(w);
                try {
                    entity(clone).readNbt(entity(part).writeNbt(new net.minecraft.nbt.NbtCompound()));
                    require(clone.cells().equals(part.cells())&&entity(clone).getUuid().equals(entity(part).getUuid()),"native fragment grid/identity NBT round-trip failed");
                    require(Math.abs(com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions.dot(clone.rotation(),part.rotation()))>1-1e-12&&clone.physics().linearVelocity().equals(part.physics().linearVelocity())&&clone.physics().angularMomentum().equals(part.physics().angularMomentum()),"native saved fragment lost its physical state");
                    require(clone.massPosition().minus(part.massPosition()).length()<1e-9&&BodySpatialIndex.loaded(w).size()==indexed,"native unspawned NBT inspection changed pose or duplicated a live index entry");
                }finally{entity(clone).discard();}
            }
            freeze(saved);pairBodies.addAll(saved);
        }
        pairPositions=pairBodies.stream().map(BlockBodyEntity::massPosition).toArray(Vec3[]::new);pairRotations=pairBodies.stream().map(BlockBodyEntity::rotation).toArray(Quat[]::new);
        ids=new int[]{entity(a).getId(),entity(b).getId(),entity(hollow).getId(),entity(deck).getId(),entity(column).getId(),entity(fastA).getId(),entity(fastB).getId(),entity(chain.get(0)).getId(),entity(chain.get(1)).getId(),entity(chain.get(2)).getId(),entity(offset).getId(),entity(striker).getId()};
        if(FRACTURE)ids=java.util.stream.IntStream.concat(Arrays.stream(ids).limit(5),pairBodies.stream().mapToInt(body->entity(body).getId())).toArray();ready=true;
        System.out.println("BODY_CONTACT_NATIVE geometry, relative CCD, pair reaction, gravity support and unknown-Y rejection PASS");
    }
    private static List<BlockBodyEntity.Cell> line(int size) {
        var cells=new ArrayList<BlockBodyEntity.Cell>();for(int x=0;x<size;x++)cells.add(new BlockBodyEntity.Cell(new BlockPos(x,0,0),Blocks.IRON_BLOCK.getDefaultState()));return cells;
    }
    private static List<BlockBodyEntity> fracture(MinecraftClient c,BlockBodyEntity root,int count,int cut,Quat turn,Vec3 velocity,Vec3 spin) {
        var world=c.getServer().getOverworld();var before=new HashSet<Integer>();for(var body:BodySpatialIndex.loaded(world))before.add(entity(body).getId());
        root.physics().withRotation(turn).withLinearVelocity(velocity).withAngularVelocity(spin);
        var points=new HashMap<BlockPos,Vec3d>();var speeds=new HashMap<BlockPos,Vec3d>();
        for(var cell:root.cells()){var point=root.localToWorld(Vec3d.ofCenter(cell.pos()));points.put(cell.pos(),point);speeds.put(cell.pos(),root.surfaceVelocityAt(point));}
        var origin=root.massPosition();var removed=new BlockPos(cut,0,0);double mass=root.physicsMass()/count;
        var removedVelocity=pure(speeds.get(removed));
        var expectedP=velocity.scaled(root.physicsMass()).minus(removedVelocity.scaled(mass));
        var expectedL=root.physics().angularMomentum().minus(pure(points.get(removed)).minus(origin).cross(removedVelocity.scaled(mass))).minus(spin.scaled(mass/6));
        require(root.setLocalBlock(removed,Blocks.AIR.getDefaultState()),"native bridge edit failed");
        var parts=new ArrayList<BlockBodyEntity>();parts.add(root);for(var body:BodySpatialIndex.loaded(world))if(!before.contains(entity(body).getId()))parts.add(body);
        require(parts.size()==2,"native fracture did not create exactly one independent connected child");
        var owned=new HashSet<BlockPos>();Vec3 momentum=new Vec3(0,0,0),angular=momentum;
        for(var part:parts) {
            require(part.physics().angularVelocity().minus(spin).length()<1e-8,"native separated inertia lost the material spin");
            for(var cell:part.cells()) {
                require(owned.add(cell.pos()),"native fracture duplicated a material cell");var point=part.localToWorld(Vec3d.ofCenter(cell.pos()));
                require(point.distanceTo(points.get(cell.pos()))<1e-8&&part.surfaceVelocityAt(point).distanceTo(speeds.get(cell.pos()))<1e-8,"native fragment changed a remaining material position/velocity");
            }
            var p=part.physics().linearVelocity().scaled(part.physicsMass());momentum=momentum.plus(p);angular=angular.plus(part.physics().angularMomentum().plus(part.massPosition().minus(origin).cross(p)));
        }
        require(owned.size()==count-1&&!owned.contains(removed)&&momentum.minus(expectedP).length()<1e-7&&angular.minus(expectedL).length()<1e-7,"native fragments lost or duplicated surviving mass/momentum");
        return parts;
    }
    private static void freeze(List<BlockBodyEntity> parts){for(var body:parts){body.physics().withLinearVelocity(new Vec3(0,0,0)).withAngularMomentum(new Vec3(0,0,0));body.publishPhysicsState();}}
    private static Vec3 pure(Vec3d p){return new Vec3(p.x,p.y,p.z);}
    public static void rendered() {
        if(!ready || finished)return;var c=MinecraftClient.getInstance();
        for(int id:ids)if(!(c.world.getEntityById(id) instanceof BlockBodyEntity))return;
        int start=stage<5?stage:stage==5?5:stage==6?7:stage==7?10:12+(stage-8)*2,end=stage<5?start+1:stage==5?7:stage==6?10:stage==7?12:start+2;
        boolean visible=true;for(int i=start;i<end;i++)visible&=DRAWN.contains(ids[i]);DRAWN.clear();
        if(stage>=5&&frames>30) {
            var errors=new double[end-start];boolean awaitingAbsolute=false;
            for(int i=start;i<end;i++) {
                var body=(BlockBodyEntity)c.world.getEntityById(ids[i]);double error=body.massPosition().minus(pairPositions[i-5]).length();errors[i-start]=error;
                // Relative entity positions use1/4096-block deltas. The same frozen pose
                // may arrive via an exact spawn or a quantized delta, depending on tracking
                // readiness. Keep the final1e-5 assertion; allow only that wire bound while
                // waiting for the normal absolute refresh (tracking interval1, ~400 ticks).
                if(error>=1e-5) {
                    require(error<=Math.sqrt(3)/4096+1e-6,"native hull pose exceeds relative-packet precision: "+error);
                    awaitingAbsolute=true;maxQuantizedPoseError=Math.max(maxQuantizedPoseError,error);
                }
                require(Math.abs(com.terminaldetector.drmd.vendor.immptl.ImmPtlQuaternions.dot(body.rotation(),pairRotations[i-5]))>1-1e-8,"native contact rotation was not synchronized");
            }
            if(awaitingAbsolute){precisionWaitSamples++;require(++precisionWaitFrames<=900,"native absolute pose refresh never reached the strict final tolerance");return;}
            for(double error:errors){require(error<1e-5,"native final precise hull pose diverged: "+error);maxPairClientError=Math.max(maxPairClientError,error);pairClientSamples++;}
        }
        if(!visible || ++frames<90)return;
        try(var image=ScreenshotRecorder.takeScreenshot(c.getFramebuffer())) {
            var path=c.runDirectory.toPath().resolve("body-contact-frames");Files.createDirectories(path);image.writeTo(path.resolve(NAMES[stage]+".png"));
            if(FRACTURE&&stage==9) {
                require(c.options.getCloudRenderMode().getValue()==net.minecraft.client.option.CloudRenderMode.OFF,"clouds must not occlude the fast fragment proof");
                var camera=c.gameRenderer.getCamera();var cq=camera.getRotation();var inverse=new Quat(cq.x,cq.y,cq.z,cq.w).inverse();
                for(int n=0;n<2;n++) {
                    var body=(BlockBodyEntity)c.world.getEntityById(ids[14+n]);var point=inverse.rotate(body.massPosition().minus(pure(camera.getPos())));
                    require(point.z()<-1,"fast fragment centre is behind the proof camera");
                    double focal=image.getHeight()/(2*Math.tan(Math.toRadians(c.options.getFov().getValue())/2));
                    int px=(int)Math.round(image.getWidth()/2.+point.x()*focal/-point.z()),py=(int)Math.round(image.getHeight()/2.-point.y()*focal/-point.z());
                    for(int x=Math.max(0,px-10);x<=Math.min(image.getWidth()-1,px+10);x++)for(int y=Math.max(0,py-10);y<=Math.min(image.getHeight()-1,py+10);y++) {
                        int colour=image.getColor(x,y),r=colour&255,g=(colour>>>8)&255,b=(colour>>>16)&255;
                        if(r>30&&r<235&&g>30&&b>30&&Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<72)fastVisiblePixels[n]++;
                    }
                    require(fastVisiblePixels[n]>=8,"fast fragment was drawn but not visibly present near its projected centre: id="+ids[14+n]+" px="+px+" py="+py+" visible="+fastVisiblePixels[n]);
                }
            }
            RESULTS.add("{\"scene\":\""+NAMES[stage]+"\",\"renderedFrames\":"+frames+"}");
            System.out.println("BODY_CONTACT_CLIENT "+NAMES[stage]+" PASS");
            if(stage==3)require(settledRotationFrames>=50,"insufficient actual stationary rotation frames");
            frames=0;precisionWaitFrames=0;if(++stage==3)c.getServer().execute(()->{
                var body=c.getServer().getOverworld().getEntityById(ids[3]);
                require(body instanceof BlockBodyEntity,"native deck disappeared before turn");
                ((BlockBodyEntity)body).physics().withRotation(STOPPED_TURN);
            });
            if(stage==NAMES.length) {
                Files.writeString(c.runDirectory.toPath().resolve("body-contacts-ok.json"),"{\"orientedTerrain\":true,\"hollowRay\":true,\"unknownNativeYRejected\":true,\"compoundContactQuery\":true,\"relativeCCD\":true,\"pairReaction\":true,\"nativeGravitySupport\":true,\"stableRenderedRotationFrames\":"+settledRotationFrames+",\"actualRenderer\":true,\"playerBoarding\":false,\"actualHullPairs\":true,\"wholeTickIntegrated\":true,\"sequentialThreeHulls\":true,\"rotatedHullImpulse\":true,\"maxPairClientError\":"+maxPairClientError+",\"maxQuantizedPoseError\":"+maxQuantizedPoseError+",\"precisionWaitSamples\":"+precisionWaitSamples+",\"pairClientSamples\":"+pairClientSamples+",\"actualFragments\":"+FRACTURE+",\"continuousMaterialMotion\":"+FRACTURE+",\"fastFragmentTickIntegrated\":"+FRACTURE+",\"minFastFragmentSpeed\":"+(FRACTURE?minFastFragmentSpeed:0)+",\"fastVisiblePixels\":"+Arrays.toString(fastVisiblePixels)+",\"fragmentNbtRoundTrip\":"+FRACTURE+",\"fullWorldReload\":false,\"scenes\":["+String.join(",",RESULTS)+"]}\n");
                finished=true;c.scheduleStop();
            }
        }catch(java.io.IOException error){throw new IllegalStateException(error);}
    }
}
