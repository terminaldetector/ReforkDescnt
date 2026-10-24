package com.terminaldetector.drmd;

import com.terminaldetector.drmd.client.*;
import com.terminaldetector.drmd.client.flight.ShipAttitudeClient;
import com.terminaldetector.drmd.client.render.WeaponLoadState;
import com.terminaldetector.drmd.entity.*;
import com.terminaldetector.drmd.physics.GravyPhysics;
import com.terminaldetector.drmd.weapon.items.ModItems;
import com.terminaldetector.drmd.workshop.*;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.util.WorldSavePath;
import java.nio.file.Files;
import java.util.List;

/** Follows the recovered projectile checks in the same disposable real client. */
public final class HybridWeaponCheck {
    private static final String[] MODELS={HybridWeaponModels.IMPULSE,HybridWeaponModels.PLASMA,HybridWeaponModels.RAIL};
    private static final Vec3d POSITION=new Vec3d(12,205,30);
    private static boolean active;
    private static int model, tick, total, captures, draws;
    private static float maxRecoil;
    private static volatile boolean serverReady, diskVerified;
    private static volatile int fired, held;
    private static WorkshopScreen workshop;
    private static String pending;
    private static Runnable after;
    private static boolean gui;
    private static Vec3d forward=new Vec3d(0,0,-1),up=new Vec3d(0,1,0);
    private static final java.util.Set<String> PROBED=new java.util.HashSet<>();
    private HybridWeaponCheck() {}
    public static boolean active() { return active; }
    public static void recordDraw(ClusterModule module,float recoil) {
        if(!active) return;
        PROBED.add(module.model); draws++; maxRecoil=Math.max(maxRecoil,recoil);
    }
    public static void start(MinecraftClient client) {
        active=true; client.options.getGuiScale().setValue(2); client.onResolutionChanged();
        WorldRenderEvents.END.register(context->{if(active && pending!=null && !gui) capture(client);});
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(!active) return;
            var player=server.getPlayerManager().getPlayer(client.player.getUuid());
            if(player==null) return;
            player.setPosition(POSITION); player.setVelocity(Vec3d.ZERO);
            var data=DescentPlayerData.get(player); data.setFlightVelocity(Vec3d.ZERO);
            data.setShipAttitude((float)forward.x,(float)forward.y,(float)forward.z,(float)up.x,(float)up.y,(float)up.z);
        });
        prepare(client);
    }
    public static void guiRendered() {
        if(active && gui && pending!=null) capture(MinecraftClient.getInstance());
    }
    private static void prepare(MinecraftClient client) {
        tick=0; serverReady=false; diskVerified=false; held=fired=0; draws=0; maxRecoil=0;
        PROBED.clear(); forward=new Vec3d(0,0,-1); up=new Vec3d(0,1,0);
        var server=client.getServer(); var uuid=client.player.getUuid();
        server.execute(()->{
            var player=server.getPlayerManager().getPlayer(uuid); var world=server.getOverworld();
            player.stopRiding(); GravyPhysics.release(player);
            player.changeGameMode(net.minecraft.world.GameMode.CREATIVE);
            for(var entity:world.iterateEntities()) if(entity instanceof ProjectileEntity || entity instanceof PyroShipEntity) entity.discard();
            player.teleport(world,POSITION.x,POSITION.y,POSITION.z,180,0);
            player.setStackInHand(Hand.MAIN_HAND,new ItemStack(switch(model) {
                case 0->ModItems.LASER_PULSE; case 1->ModItems.PLASMA; default->ModItems.GRAVY_RAILGUN;
            }));
            var data=DescentPlayerData.get(player); data.setEnabled(true); data.setEnergy(100); data.setGravyEnergy(100);
            ConstructionRegistry.clearOverride(HybridWeaponModels.weapon(MODELS[model]));
            world.setTimeOfDay(6000); serverReady=true;
        });
    }
    public static void tick(MinecraftClient client) {
        if(++total>2400) throw new IllegalStateException("Hybrid check timeout model="+model+" tick="+tick);
        if(!serverReady || pending!=null) return;
        DescentClientState.enabled=true; DescentClientState.attitudeValid=true;
        client.player.setPosition(POSITION); client.player.setVelocity(Vec3d.ZERO);
        ShipAttitudeClient.acceptPortalFrame(client.player,forward,up);
        tick++;
        if(tick==20) {
            workshop=new WorkshopScreen(); client.setScreen(workshop);
            workshop.selectHybridSample(MODELS[model]); workshop.savePreset();
        }
        if(tick==25) {
            if(workshop.previewFaceCount()<300) throw new IllegalStateException("No actual 3D preview mesh");
            queue("hybrid-"+model+"-workshop",true,()->{
                String name=MODELS[model];
                workshop.selectHybridSample(MODELS[(model+1)%3]);
                for(var child:workshop.children()) if(child instanceof TextFieldWidget field && field.getText().startsWith("hybrid_")) field.setText(name);
                workshop.loadPreset();
                if(!workshop.draftModules().getFirst().model.equals(name)) throw new IllegalStateException("Preset did not restore selected geometry");
                workshop.publishConstruction();
            });
        }
        if(tick==35) {
            if(!workshop.acceptedWeaponId().equals(HybridWeaponModels.weapon(MODELS[model]))) { tick--; return; }
            client.getServer().execute(()->{
                String id=HybridWeaponModels.weapon(MODELS[model]);
                var file=client.getServer().getSavePath(WorldSavePath.ROOT).resolve("data/drmd/visual/"+id+".nbt");
                try {
                    var root=net.minecraft.nbt.NbtIo.readCompressed(file,net.minecraft.nbt.NbtSizeTracker.of(1024*1024));
                    var modules=ClusterModule.listFromNbt(root.getList("modules",net.minecraft.nbt.NbtElement.COMPOUND_TYPE));
                    if(modules.size()!=2 || !modules.getFirst().model.equals(MODELS[model])) throw new IllegalStateException("Server saved wrong mesh");
                    if(ConstructionRegistry.getMuzzles(id).size()!=2) throw new IllegalStateException("Server lost physical muzzle anchors");
                    diskVerified=true;
                }catch(java.io.IOException e){throw new IllegalStateException(e);}
            });
        }
        if(tick==40) { if(!diskVerified) {tick--;return;} client.setScreen(null); }
        if(tick==50) client.options.getFov().setValue(70);
        if(tick==55) queue("hybrid-"+model+"-fov70",false,()->{});
        if(tick==60) client.options.getFov().setValue(80);
        if(tick==65) queue("hybrid-"+model+"-fov80",false,()->{});
        if(tick==70) client.options.getFov().setValue(90);
        if(tick==75) queue("hybrid-"+model+"-fov90",false,()->{});
        if(tick==80) {client.options.getFov().setValue(80);up=new Vec3d(1,0,0);}
        if(tick==90) queue("hybrid-"+model+"-banked",false,()->{});
        if(tick==95) {forward=new Vec3d(0,1,0);up=new Vec3d(0,0,1);}
        if(tick==105) queue("hybrid-"+model+"-vertical",false,()->{});
        if(tick==110) {forward=new Vec3d(0,0,-1);up=new Vec3d(0,1,0);}
        if(tick==120) {
            client.getServer().execute(()->{
                var player=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                if(model==2) {
                    // Real light physical target, then the normal weapon use launches it.
                    var item=new net.minecraft.entity.ItemEntity(player.getWorld(),12,206,23,new ItemStack(net.minecraft.item.Items.IRON_BLOCK,64));
                    player.getWorld().spawnEntity(item);
                    held=GravyPhysics.tryGrab(player,GravyPhysics.Mode.RAIL,List.of(item));
                }
            });
        }
        if(tick==130 && model==2) {
            if(held!=1 || WeaponLoadState.get(client.player.getId())<=0) throw new IllegalStateException("Gravity load did not synchronize");
            queue("hybrid-2-loaded",false,()->{});
        }
        if(tick==135) {
            client.getServer().execute(()->{
                var player=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                player.getMainHandStack().getItem().use(player.getWorld(),player,Hand.MAIN_HAND);
                fired=1;
            });
        }
        if(tick>=137 && tick<=142) queue("hybrid-"+model+"-fire-"+tick,false,()->{});
        if(tick==150) {
            if(fired!=1 || maxRecoil<.1f) throw new IllegalStateException("Real use did not animate mechanisms: "+maxRecoil);
            if(!PROBED.contains(MODELS[model])) throw new IllegalStateException("Hybrid world renderer was not reached");
            if(++model<3) prepare(client); else finish(client);
        }
    }
    private static void queue(String name,boolean screen,Runnable next) {pending=name;gui=screen;after=next;}
    private static void capture(MinecraftClient client) {
        String name=pending;
        try(var image=ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
            var directory=client.runDirectory.toPath().resolve("screenshots");Files.createDirectories(directory);
            image.writeTo(directory.resolve(name+".png"));
            if(!gui && !name.contains("fire-")) {
                int accent=0;
                for(int y=image.getHeight()/2;y<image.getHeight()-30;y++) for(int x=40;x<image.getWidth()-40;x++) {
                    int c=image.getColor(x,y),r=c&255,g=(c>>8)&255,b=(c>>16)&255;
                    boolean colour=switch(model) {case 0->r>80&&r>g*1.2&&g>b*1.2;case 1->g>80&&b>80&&b>r*1.4;default->b>60&&b>g*1.2&&r>g*1.1;};
                    if(colour)accent++;
                }
                if(accent<12)throw new IllegalStateException(name+": no visible model accents: "+accent);
            }
            captures++;
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
        pending=null; after.run();
    }
    private static void finish(MinecraftClient client) {
        try {Files.writeString(client.runDirectory.toPath().resolve("hybrid-weapons-ok.json"),
                "{\"models\":3,\"presetRoundTrips\":3,\"serverSaves\":3,\"muzzlePairs\":3,\"fovs\":[70,80,90],\"banked\":3,\"vertical\":3,\"realUseAnimated\":3,\"loadSynced\":true,\"frames\":"+captures+"}\n");
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
        active=false; client.scheduleStop();
    }
}
