package com.terminaldetector.drmd.gate;

import com.google.gson.Gson;
import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.client.DescentClientState;
import com.terminaldetector.drmd.client.input.DescentKeybinds;
import com.terminaldetector.drmd.client.render.WeaponLoadState;
import com.terminaldetector.drmd.client.screen.DroneInventoryScreen;
import com.terminaldetector.drmd.flight.FlightSystem;
import com.terminaldetector.drmd.inventory.*;
import com.terminaldetector.drmd.network.ModNetworking;
import com.terminaldetector.drmd.physics.GravyPhysics;
import com.terminaldetector.drmd.weapon.core.WeaponCore;
import com.terminaldetector.drmd.weapon.items.ModItems;
import com.terminaldetector.drmd.world.access.CubicAvailability;
import com.terminaldetector.drmd.world.contraption.BlockBodyEntity;
import com.terminaldetector.drmd.world.edit.WorldEditSafety;
import com.terminaldetector.drmd.world.gravity.TransientGravityFields;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.item.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import org.lwjgl.glfw.GLFW;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** One actual-client route observer. Only keys/UI select and operate the registered gravity items. */
public final class NativeGravityInputRouteCheck {
    private static final long BOOT=600,SELECT=120,ENERGY=96,FIXTURE=100,TRANSACTION=160,MODE=60,TOTAL=3600;
    private static final long WALL_NANOS=300_000_000_000L;
    private static final Gson JSON=new Gson();
    private static final double X=96.5,Y=Integer.getInteger("drmd.boarding.labY",2156),Z=96.5;
    private record Scene(String name,boolean flight,GravyPhysics.Mode mode,Item item,int hotbar,int channel,int mask,float captureCost,float throwCost){
        int count(){return mode==GravyPhysics.Mode.SWARM?4:1;}
        int cells(){return mode==GravyPhysics.Mode.RAIL?100:1;}
        float budget(){return captureCost+throwCost;}
    }
    private static final Scene[] SCENES={
        new Scene("ordinary-swarm-lmb",false,GravyPhysics.Mode.SWARM,ModItems.GRAVITY_SWARM,0,0,1,18,10),
        new Scene("ordinary-rail-rmb",false,GravyPhysics.Mode.RAIL,ModItems.GRAVY_RAILGUN,1,1,2,16,12),
        new Scene("ordinary-shift-lmb",false,GravyPhysics.Mode.SHIFT,ModItems.GRAVITY_SHIFTER,2,0,1,20,18),
        new Scene("flight-swarm-lmb",true,GravyPhysics.Mode.SWARM,ModItems.GRAVITY_SWARM,0,0,1,18,10),
        new Scene("flight-rail-rmb",true,GravyPhysics.Mode.RAIL,ModItems.GRAVY_RAILGUN,1,1,2,16,12),
        new Scene("flight-shift-rmb",true,GravyPhysics.Mode.SHIFT,ModItems.GRAVITY_SHIFTER,2,1,2,20,18)
    };
    private enum Step {BOOTSTRAP,SELECT,ENERGY,FIXTURE,CAPTURE,HOLD,THROW,VERIFY,RETIRE,TOGGLE,COMPLETE}
    private record View(long tick,long time,boolean nativeBackend,boolean configured,boolean floorReady,boolean flight,boolean open,int selected,String hand,List<String> hotbar,List<String> sockets,String cursor,float energy,float cooldown,GravityInputRouteTrace.Grab grab,List<GravityInputRouteTrace.Target> targets,boolean loadedRay){}
    private record UiClick(int slot,String cursor,String after){}
    private static volatile UUID playerId;
    private static volatile View view;
    private static volatile long serverTicks;
    private static volatile boolean configured,floorReady;
    private static volatile int fixtureScene=-1,retiredScene=-1,maxLiveBodies;
    private static volatile String serverFailure;
    private static List<UUID> owned=List.of(); // server-thread owned, published only through immutable snapshots
    private static final List<Map<String,Object>> RESULTS=new ArrayList<>(),KEYS=new ArrayList<>();
    private static final List<String> FRAMES=new ArrayList<>();
    private static final Set<KeyBinding> OWN_KEYS=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final ArrayDeque<UiClick> uiPlan=new ArrayDeque<>();
    private static Step step=Step.BOOTSTRAP;
    private static int scene,clientTicks,uiStage,captureFrames;
    private static long registeredAt,globalStart,entered,limit,transactionSequence,energyStart,energyWaitTicks;
    private static boolean started,selectedSent,fixtureRequested,retireRequested,toggleSent,finished,uiPlanned;
    private static String failure,captureName;
    private static long failedAt,uiClickCount;
    private static KeyBinding pressed;
    private static int pressedAt;
    private static UiClick pendingClick;
    private static GravityInputRouteTrace.Use captureUse,throwUse;
    private static double holdTravel;
    private NativeGravityInputRouteCheck(){}

    public static void register(){
        registeredAt=System.nanoTime();
        ServerTickEvents.END_SERVER_TICK.register(NativeGravityInputRouteCheck::serverTick);
        ClientTickEvents.START_CLIENT_TICK.register(NativeGravityInputRouteCheck::tick);
        WorldRenderEvents.END.register(context->rendered());
    }
    private static Scene current(){return SCENES[Math.min(scene,SCENES.length-1)];}
    private static String item(ItemStack stack){return GravityInputRouteTrace.item(stack);}
    private static String expectedItem(){return net.minecraft.registry.Registries.ITEM.getId(current().item()).toString();}
    private static Path file(String name){return MinecraftClient.getInstance().runDirectory.toPath().resolve(name);}
    private static void submit(MinecraftClient client,Runnable action){client.getServer().execute(()->{try{action.run();}catch(Throwable error){serverFailure=error.toString();error.printStackTrace();}});}
    private static ServerPlayerEntity player(MinecraftServer server){return server.getPlayerManager().getPlayer(playerId);}
    private static void requireServer(boolean yes,String message){if(!yes)throw new IllegalStateException(message);}
    private static boolean require(MinecraftClient client,boolean yes,String why){if(!yes)fail(client,why);return yes;}
    private static void progress(){System.out.println("GRAVITY_INPUT_STEP scene="+scene+" step="+step+" serverTick="+serverTicks+" capacitor="+(view==null?"unknown":view.energy()));}
    private static void enter(Step value,long budget){step=value;entered=serverTicks;limit=budget;progress();}
    private static void pulse(KeyBinding binding){
        if(pressed!=null)throw new IllegalStateException("overlapping test key pulses");
        binding.setPressed(true);KeyBinding.onKeyPressed(KeyBindingHelper.getBoundKeyOf(binding));pressed=binding;pressedAt=clientTicks;OWN_KEYS.add(binding);
        KEYS.add(Map.of("scene",scene,"step",step.name(),"clientTick",clientTicks,"serverTick",serverTicks,"binding",binding.getTranslationKey(),"key",KeyBindingHelper.getBoundKeyOf(binding).getTranslationKey()));
    }
    private static void releasePulse(){if(pressed!=null&&clientTicks>pressedAt){pressed.setPressed(false);pressed=null;}}
    private static void releaseKeys(){OWN_KEYS.forEach(key->key.setPressed(false));pressed=null;}
    private static void bootstrap(MinecraftServer server){
        var p=player(server);requireServer(p!=null,"real connected observer player");
        p.changeGameMode(GameMode.CREATIVE);FlightSystem.disable(p,DescentPlayerData.get(p));
        p.teleport(p.getServerWorld(),X,Y,Z,Set.of(),0,0);
        p.getAbilities().allowFlying=true;p.getAbilities().flying=true;p.setNoGravity(true);p.sendAbilitiesUpdate();
        p.getInventory().clear();p.getInventory().setStack(0,new ItemStack(ModItems.GRAVITY_SWARM));p.getInventory().setStack(1,new ItemStack(ModItems.GRAVY_RAILGUN));p.getInventory().setStack(2,new ItemStack(ModItems.GRAVITY_SHIFTER));p.getInventory().selectedSlot=8;
        p.getInventory().markDirty();p.playerScreenHandler.sendContentUpdates();ModNetworking.syncPlayer(p,DescentPlayerData.get(p));
        requireServer(!p.hasVehicle()&&!com.terminaldetector.drmd.world.mega.UfoControlSystem.controls(p),"fixture starts as the actual free player without a captured console");configured=true;
    }
    private static void serverTick(MinecraftServer server){
        serverTicks=server.getTicks();if(playerId==null||finished)return;var p=player(server);if(p==null)return;
        try{
            var w=p.getServerWorld();boolean nativeBackend=CubicAvailability.backend(w)==CubicAvailability.Backend.NATIVE_CUBIC;
            var start=p.getEyePos();var end=start.add(WeaponCore.aimDir(p).normalize().multiply(24));
            boolean loaded=nativeBackend&&WorldEditSafety.loadedSegment(w,start,end);
            if(configured&&!floorReady&&loaded){
                var centre=new BlockPos((int)Math.floor(X),(int)Y-1,(int)Math.floor(Z));boolean ready=true;
                for(var at:BlockPos.iterate(centre.add(-3,0,-3),centre.add(3,0,3)))ready&=WorldEditSafety.neighbourhoodLoaded(w,at);
                if(ready){for(var at:BlockPos.iterate(centre.add(-3,0,-3),centre.add(3,0,3)))w.setBlockState(at,Blocks.STONE.getDefaultState(),2);floorReady=true;}
            }
            var hotbar=new ArrayList<String>();for(int i=0;i<9;i++)hotbar.add(item(p.getInventory().getStack(i)));
            var sockets=new ArrayList<String>();
            if(DescentPlayerData.get(p).isEnabled()||p.currentScreenHandler instanceof DroneInventoryHandler){var slots=DroneLoadout.of(p);for(int i=0;i<5;i++)sockets.add(item(slots.getStack(i)));}
            else for(int i=0;i<5;i++)sockets.add("unread");
            view=new View(serverTicks,w.getTime(),nativeBackend,configured,floorReady,DescentPlayerData.get(p).isEnabled(),p.currentScreenHandler instanceof DroneInventoryHandler,p.getInventory().selectedSlot,item(p.getMainHandStack()),List.copyOf(hotbar),List.copyOf(sockets),item(p.currentScreenHandler.getCursorStack()),DescentPlayerData.get(p).getGravyEnergy(),p.getItemCooldownManager().getCooldownProgress(current().item(),0),GravityInputRouteTrace.grab(p),GravityInputRouteTrace.targets(w),loaded);
        }catch(Throwable error){serverFailure=error.toString();error.printStackTrace();}
    }
    private static void createFixture(MinecraftServer server,int phase){
        var p=player(server);var w=p.getServerWorld();requireServer(phase==scene&&GravyPhysics.heldCount(p)==0&&owned.isEmpty(),"create bodies only between completed transactions");
        var cfg=SCENES[phase];var start=p.getEyePos();var aim=WeaponCore.aimDir(p).normalize();
        requireServer(WorldEditSafety.loadedSegment(w,start,start.add(aim.multiply(24))),"fixture requires the actual loaded collision halo");
        var centre=start.add(aim.multiply(cfg.mode()==GravyPhysics.Mode.RAIL?12:8));
        var side=aim.crossProduct(Math.abs(aim.y)<.9?new Vec3d(0,1,0):new Vec3d(1,0,0)).normalize();var up=side.crossProduct(aim).normalize();
        var ids=new ArrayList<UUID>();
        if(cfg.mode()==GravyPhysics.Mode.SWARM)for(int x:new int[]{-1,1})for(int y:new int[]{-1,1})ids.add(body(w,centre.add(side.multiply(x*1.2)).add(up.multiply(y*1.2)),1,1,1));
        else ids.add(cfg.mode()==GravyPhysics.Mode.RAIL?body(w,centre,5,5,4):body(w,centre,1,1,1));
        requireServer(ids.size()<=4,"four owned live targets maximum");owned=List.copyOf(ids);maxLiveBodies=Math.max(maxLiveBodies,owned.size());GravityInputRouteTrace.targets(owned);fixtureScene=phase;
    }
    private static UUID body(ServerWorld world,Vec3d centre,int sx,int sy,int sz){
        var cells=new ArrayList<BlockBodyEntity.Cell>();for(int x=0;x<sx;x++)for(int y=0;y<sy;y++)for(int z=0;z<sz;z++)cells.add(new BlockBodyEntity.Cell(new BlockPos(x,y,z),Blocks.IRON_BLOCK.getDefaultState()));
        Entity actual=BlockBodyEntity.createLocal(world,cells,centre.subtract(sx*.5,sy*.5,sz*.5));return actual.getUuid();
    }
    private static void retire(MinecraftServer server,int phase){
        var p=player(server);requireServer(phase==scene&&GravyPhysics.heldCount(p)==0,"retire bodies only after the completed throw");
        for(var id:owned){var field=UUID.nameUUIDFromBytes(("drmd:gravity-projector:"+id).getBytes(StandardCharsets.UTF_8));TransientGravityFields.remove(field);requireServer(TransientGravityFields.remainingTicks(field)==0,"owned throw field is retired");Entity e=p.getServerWorld().getEntity(id);if(e!=null)e.discard();}
        owned=List.of();GravityInputRouteTrace.targets(List.of());retiredScene=phase;
    }
    private static void beginScene(){
        GravityInputRouteTrace.scene(scene);selectedSent=fixtureRequested=retireRequested=toggleSent=uiPlanned=false;uiStage=0;pendingClick=null;uiPlan.clear();captureUse=throwUse=null;holdTravel=0;captureName=null;
        enter(scene==3?Step.TOGGLE:Step.SELECT,scene==3?MODE:SELECT);
    }
    private static String clientSlot(HandledScreen<?> screen,int slot){return item(screen.getScreenHandler().getSlot(slot).getStack());}
    private static String serverSlot(int slot){return slot<5?view.sockets().get(slot):view.hotbar().get(slot-32);}
    private static boolean inventorySynchronized(HandledScreen<?> screen){
        if(!view.open()||!item(screen.getScreenHandler().getCursorStack()).equals(view.cursor()))return false;
        for(int i=0;i<5;i++)if(!clientSlot(screen,i).equals(view.sockets().get(i)))return false;
        return true;
    }
    private static void click(HandledScreen<?> screen,int slot){
        var actual=screen.getScreenHandler().getSlot(slot);double x=(screen.width-176)/2.0+actual.x+8,y=(screen.height-212)/2.0+actual.y+8;
        screen.mouseClicked(x,y,0);screen.mouseReleased(x,y,0);
    }
    private static void select(MinecraftClient client){
        var cfg=current();
        if(!require(client,view.flight()==cfg.flight()&&DescentClientState.enabled==cfg.flight(),"actual server/client movement mode changed unexpectedly"))return;
        if(!cfg.flight()){
            if(!selectedSent){pulse(client.options.hotbarKeys[cfg.hotbar()]);selectedSent=true;return;}
            if(client.player.getInventory().selectedSlot==cfg.hotbar()&&view.selected()==cfg.hotbar()&&item(client.player.getMainHandStack()).equals(expectedItem())&&view.hand().equals(expectedItem())&&GravityInputRouteTrace.selections(scene,"hotbar")>0){energyStart=serverTicks;enter(Step.ENERGY,ENERGY);}return;
        }
        if(!selectedSent){pulse(client.options.hotbarKeys[8]);selectedSent=true;return;}
        if(client.player.getInventory().selectedSlot!=8||view.selected()!=8||!client.player.getMainHandStack().isEmpty()||!view.hand().equals("minecraft:air"))return;
        if(uiStage==0){pulse(DescentKeybinds.customize);uiStage=1;return;}
        if(uiStage==3){
            if(client.currentScreen==null&&!view.open()&&view.sockets().get(cfg.channel()).equals(expectedItem())){energyStart=serverTicks;enter(Step.ENERGY,ENERGY);}return;
        }
        if(!(client.currentScreen instanceof DroneInventoryScreen)||!view.open())return;
        HandledScreen<?> screen=(HandledScreen<?>)client.currentScreen;if(!inventorySynchronized(screen))return;
        if(!uiPlanned){
            if(!require(client,screen.getScreenHandler().getCursorStack().isEmpty(),"equipment UI starts with an empty real cursor"))return;
            int target=cfg.channel(),source=32+cfg.hotbar();String old=clientSlot(screen,target);
            if(!old.equals("minecraft:air")){
                int spare=-1;for(int i=5;i<8;i++)if(clientSlot(screen,32+i).equals("minecraft:air")){spare=32+i;break;}
                if(!require(client,spare>=0,"owned equipment UI needs one empty inventory destination"))return;
                uiPlan.add(new UiClick(target,old,"minecraft:air"));uiPlan.add(new UiClick(spare,"minecraft:air",old));
            }
            if(!require(client,clientSlot(screen,source).equals(expectedItem()),"normal inventory holds the tool selected for the socket"))return;
            uiPlan.add(new UiClick(source,expectedItem(),"minecraft:air"));uiPlan.add(new UiClick(target,"minecraft:air",expectedItem()));uiPlanned=true;
        }
        if(pendingClick!=null){
            if(GravityInputRouteTrace.selections(scene,"screen-click")<=uiClickCount||!view.cursor().equals(pendingClick.cursor())||!item(screen.getScreenHandler().getCursorStack()).equals(pendingClick.cursor())||!clientSlot(screen,pendingClick.slot()).equals(pendingClick.after())||!serverSlot(pendingClick.slot()).equals(pendingClick.after()))return;
            pendingClick=null;
        }
        if(!uiPlan.isEmpty()){
            pendingClick=uiPlan.removeFirst();uiClickCount=GravityInputRouteTrace.selections(scene,"screen-click");click(screen,pendingClick.slot());return;
        }
        if(!require(client,GravityInputRouteTrace.selections(scene,"screen-click")>=2&&view.sockets().get(cfg.channel()).equals(expectedItem()),"normal UI click packets selected the actual server socket"))return;
        screen.keyPressed(GLFW.GLFW_KEY_ESCAPE,0,0);uiStage=3;
    }
    private static boolean clientTargets(MinecraftClient client){
        if(view.targets().size()!=current().count())return false;
        for(var target:view.targets()){
            Entity e=client.world.getEntityById(target.entityId());
            if(e==null||!e.getUuid().equals(target.id())||!(e instanceof BlockBodyEntity body)||body.cells().size()!=current().cells())return false;
        }return true;
    }
    private static double distance(GravityInputRouteTrace.Target a,GravityInputRouteTrace.Target b){return Math.sqrt(Math.pow(a.x()-b.x(),2)+Math.pow(a.y()-b.y(),2)+Math.pow(a.z()-b.z(),2));}
    private static double travel(List<GravityInputRouteTrace.Target> start,List<GravityInputRouteTrace.Target> now){
        double result=0;for(var a:start)for(var b:now)if(a.id().equals(b.id()))result=Math.max(result,distance(a,b));return result;
    }
    private static boolean validateUse(MinecraftClient client,GravityInputRouteTrace.Use use,boolean capturing){
        var cfg=current();
        if(!require(client,use.accepted()&&use.before().item().equals(expectedItem())&&use.before().hand().equals("MAIN_HAND"),"the real selected item accepted the actual key route: "+use))return false;
        var before=use.before().grab();var after=use.after().grab();
        double mass=cfg.mode()==GravyPhysics.Mode.RAIL?100:cfg.count();
        if(capturing){
            if(!require(client,before.count()==0&&after.count()==cfg.count()&&after.mode().equals(cfg.mode().name())&&Math.abs(after.mass()-mass)<1e-5,"actual key capture selected the expected original body set: "+use))return false;
        }else if(!require(client,before.count()==cfg.count()&&before.mode().equals(cfg.mode().name())&&Math.abs(before.mass()-mass)<1e-5&&after.count()==0,"actual second key releases the original held set: "+use))return false;
        float cost=capturing?cfg.captureCost():cfg.throwCost();
        return require(client,Math.abs(use.before().energy()-use.after().energy()-cost)<1e-5,"one synchronous actual item-use energy debit for "+cfg.name()+": "+use);
    }
    private static void tick(MinecraftClient client){
        if(finished)return;clientTicks++;releasePulse();
        if(failure!=null){if(System.nanoTime()-failedAt>5_000_000_000L){finished=true;client.scheduleStop();throw new IllegalStateException(failure+" (no fresh world frame available)");}return;}
        if(System.nanoTime()-registeredAt>WALL_NANOS||clientTicks>12000){fail(client,"bounded native gravity input wall/client deadline");return;}
        if(client.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen prompt)for(var child:prompt.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget b&&b.getMessage().getString().equals("I know what I'm doing!"))b.onPress();
        if(client.player==null||client.world==null||client.getServer()==null)return;
        if(!started){
            if(!require(client,Set.of("cubic-client-automation","gravity-inputcheck").contains(client.runDirectory.toPath().getFileName().toString()),"disposable native route runtime required"))return;
            started=true;globalStart=serverTicks;playerId=client.player.getUuid();GravityInputRouteTrace.start(playerId);entered=serverTicks;limit=BOOT;
            OWN_KEYS.add(client.options.attackKey);OWN_KEYS.add(client.options.useKey);releaseKeys();client.setScreen(null);client.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
            submit(client,()->bootstrap(client.getServer()));return;
        }
        if(serverFailure!=null){fail(client,"server-side observer/fixture failure: "+serverFailure);return;}
        if(serverTicks-globalStart>TOTAL){fail(client,"bounded3600-server-tick route deadline");return;}
        if(serverTicks-entered>limit){fail(client,"stage deadline "+step+" scene="+scene+" view="+view);return;}
        if(view==null)return;
        switch(step){
            case BOOTSTRAP->{
                if(!view.configured()||!view.floorReady()||!view.nativeBackend()||view.flight()||DescentClientState.enabled||client.player.getY()<Y-1)return;
                if(!selectedSent){pulse(client.options.hotbarKeys[8]);selectedSent=true;return;}
                if(client.player.getInventory().selectedSlot==8&&view.selected()==8&&client.player.getMainHandStack().isEmpty())beginScene();
            }
            case TOGGLE->{
                if(!toggleSent){pulse(DescentKeybinds.toggle);toggleSent=true;return;}
                if(view.flight()&&DescentClientState.enabled){selectedSent=false;enter(Step.SELECT,SELECT);}
            }
            case SELECT->select(client);
            case ENERGY->{
                if(client.currentScreen!=null||view.open())return;
                if(view.energy()>=current().budget()){energyWaitTicks=serverTicks-energyStart;fixtureRequested=false;enter(Step.FIXTURE,FIXTURE);}
            }
            case FIXTURE->{
                if(!view.loadedRay())return;
                if(!fixtureRequested){fixtureRequested=true;submit(client,()->createFixture(client.getServer(),scene));return;}
                if(fixtureScene!=scene||!clientTargets(client)||view.targets().stream().anyMatch(t->!t.present()))return;
                transactionSequence=GravityInputRouteTrace.sequence();enter(Step.CAPTURE,TRANSACTION);pulse(current().mask()==1?client.options.attackKey:client.options.useKey);
            }
            case CAPTURE->{
                var uses=GravityInputRouteTrace.uses(scene,transactionSequence);if(uses.isEmpty())return;
                if(!require(client,uses.size()==1,"one item use for the capture key pulse: "+uses))return;
                captureUse=uses.getFirst();if(!validateUse(client,captureUse,true))return;
                if(!require(client,GravityInputRouteTrace.trigger(scene,transactionSequence,current().mask()),"actual decoded trigger mask reached the server"))return;
                requestFrame(current().name()+"-held");step=Step.HOLD;progress(); // One160tick transaction budget includes capture, hold, throw, frame waits and retirement.
            }
            case HOLD->{
                if(view.time()<captureUse.tick())return;
                var uses=GravityInputRouteTrace.uses(scene,transactionSequence);
                if(!require(client,uses.size()==1&&view.grab().count()==current().count()&&view.grab().mode().equals(current().mode().name()),"capture is retained until the requested second key, not auto-thrown: "+uses))return;
                if(!GravityInputRouteTrace.trigger(scene,captureUse.sequence(),0)||view.time()-captureUse.tick()<8||view.cooldown()>0||WeaponLoadState.get(client.player.getId())<=0||!clientTargets(client)||captureName!=null)return;
                holdTravel=travel(captureUse.after().targets(),view.targets());
                if(!require(client,holdTravel>.15,"normal server hold springs move actual material before the second key"))return;
                step=Step.THROW;progress();pulse(current().mask()==1?client.options.attackKey:client.options.useKey);
            }
            case THROW->{
                var uses=GravityInputRouteTrace.uses(scene,captureUse.sequence());if(uses.isEmpty())return;
                if(!require(client,uses.size()==1,"one actual item use for the throw key pulse: "+uses))return;
                throwUse=uses.getFirst();if(!validateUse(client,throwUse,false))return;
                var expected=captureUse.after().targets().stream().map(GravityInputRouteTrace.Target::id).collect(java.util.stream.Collectors.toSet());
                var direction=throwUse.before();
                if(!require(client,throwUse.after().targets().size()==current().count()&&throwUse.after().targets().stream().allMatch(t->t.present()&&expected.contains(t.id())&&Double.isFinite(t.vx()*t.vx()+t.vy()*t.vy()+t.vz()*t.vz())&&t.vx()*direction.aimX()+t.vy()*direction.aimY()+t.vz()*direction.aimZ()>24),"real second key launches every original body with forward physical velocity"))return;
                if(current().mode()==GravyPhysics.Mode.SHIFT&&!require(client,throwUse.after().targets().stream().allMatch(t->t.fieldTicks()>0),"actual shifter key attaches its own directional field"))return;
                step=Step.VERIFY;progress();
            }
            case VERIFY->{
                if(!GravityInputRouteTrace.trigger(scene,throwUse.sequence(),0)||view.grab().count()!=0||WeaponLoadState.get(client.player.getId())>0)return;
                if(!require(client,GravityInputRouteTrace.uses(scene,transactionSequence).size()==2,"one capture and one throw, without hidden repeats"))return;
                if(current().flight()&&!require(client,client.player.getMainHandStack().isEmpty()&&view.hand().equals("minecraft:air"),"empty real hand proves6DoF socket operation"))return;
                var result=new LinkedHashMap<String,Object>();result.put("scene",current().name());result.put("flight",current().flight());result.put("channel",current().channel());result.put("mask",current().mask());result.put("hotbarPackets",GravityInputRouteTrace.selections(scene,"hotbar"));result.put("screenClickPackets",GravityInputRouteTrace.selections(scene,"screen-click"));result.put("holdTravel",holdTravel);result.put("normalHoldTicks",throwUse.tick()-captureUse.tick());result.put("capture",captureUse);result.put("throw",throwUse);result.put("naturalEnergyAdmissionTicks",energyWaitTicks);RESULTS.add(result);
                requestFrame(current().name()+"-thrown");retireRequested=false;step=Step.RETIRE;progress();
            }
            case RETIRE->{
                if(captureName!=null)return;
                if(!retireRequested){retireRequested=true;submit(client,()->retire(client.getServer(),scene));return;}
                if(retiredScene!=scene||!view.targets().isEmpty())return;
                RESULTS.getLast().put("ownedFieldsRetired",true);
                if(++scene==SCENES.length){GravityInputRouteTrace.scene(-1);enter(Step.COMPLETE,40);}else beginScene();
            }
            case COMPLETE->{
                if(!require(client,RESULTS.size()==6&&maxLiveBodies<=4&&RESULTS.stream().allMatch(r->Boolean.TRUE.equals(r.get("ownedFieldsRetired"))),"all six bounded transactions completed before PASS"))return;
                releaseKeys();write("gravity-input-route-ok.json",status("PASS"));finished=true;client.scheduleStop();
            }
        }
    }
    private static void requestFrame(String name){captureName=name;captureFrames=0;}
    private static void rendered(){
        if(!started||finished)return;var client=MinecraftClient.getInstance();if(client.world==null||client.player==null)return;
        if(failure!=null){
            try(var image=ScreenshotRecorder.takeScreenshot(client.getFramebuffer())){
                Files.createDirectories(file("gravity-input-frames"));String name="gravity-input-frames/failed-"+scene+"-"+step.name().toLowerCase(Locale.ROOT)+".png";image.writeTo(file(name));FRAMES.add(name);
                write("gravity-input-route-failed.json",status("FAIL"));
            }catch(Exception error){error.printStackTrace();}
            finished=true;client.scheduleStop();throw new IllegalStateException(failure);
        }
        if(captureName==null||client.currentScreen!=null)return;
        if(captureName.endsWith("-held")&&(view==null||WeaponLoadState.get(client.player.getId())<=0||!clientTargets(client)))return;
        if(++captureFrames<3)return;
        try(var image=ScreenshotRecorder.takeScreenshot(client.getFramebuffer())){
            Files.createDirectories(file("gravity-input-frames"));String name="gravity-input-frames/"+captureName+".png";image.writeTo(file(name));FRAMES.add(name);captureName=null;
        }catch(Exception error){fail(client,"fresh world frame could not be saved: "+error);}
    }
    private static Map<String,Object> status(String outcome){
        var result=new LinkedHashMap<String,Object>();result.put("status",outcome);result.put("scene",scene);result.put("step",step.name());result.put("failure",failure);result.put("serverFailure",serverFailure);result.put("serverTicks",serverTicks-globalStart);result.put("clientTicks",clientTicks);result.put("nativeCubic",view!=null&&view.nativeBackend());result.put("driverUsesKeyBindings",true);result.put("observedKeyEdges",KEYS.size());result.put("observerDirectItemUse",false);result.put("observerDirectTriggerReceive",false);result.put("observerEnergyWrites",false);result.put("maxLiveBodies",maxLiveBodies);result.put("serverView",view);result.put("scenes",List.copyOf(RESULTS));result.put("keys",List.copyOf(KEYS));result.put("trace",GravityInputRouteTrace.events());result.put("frames",List.copyOf(FRAMES));
        var client=MinecraftClient.getInstance();if(client.player!=null){
            var state=new LinkedHashMap<String,Object>();state.put("position",List.of(client.player.getX(),client.player.getY(),client.player.getZ()));state.put("yaw",client.player.getYaw());state.put("pitch",client.player.getPitch());state.put("flight",DescentClientState.enabled);state.put("selectedSlot",client.player.getInventory().selectedSlot);state.put("hand",item(client.player.getMainHandStack()));state.put("screen",client.currentScreen==null?"none":client.currentScreen.getClass().getSimpleName());state.put("attack",client.options.attackKey.isPressed());state.put("use",client.options.useKey.isPressed());state.put("load",WeaponLoadState.get(client.player.getId()));result.put("clientView",state);
        }
        result.put("budgets",Map.of("bootstrap",BOOT,"selection",SELECT,"naturalEnergy",ENERGY,"fixture",FIXTURE,"transaction",TRANSACTION,"mode",MODE,"totalServerTicks",TOTAL,"wallSeconds",WALL_NANOS/1_000_000_000));
        result.put("scope","one six-transaction native key/selection route; no material-hit/gravity-compensated ballistic, restart or renderer pixel-quality acceptance");return result;
    }
    private static void write(String name,Object data){try{Files.writeString(file(name),JSON.toJson(data)+"\n");}catch(Exception error){throw new IllegalStateException("could not preserve gravity input evidence "+name,error);}}
    private static void fail(MinecraftClient client,String why){
        if(failure!=null||finished)return;failure=why;failedAt=System.nanoTime();releaseKeys();
        System.err.println("GRAVITY_INPUT_ROUTE_FAIL scene="+scene+" step="+step+" why="+why+" view="+view);write("gravity-input-route-failed.json",status("FAIL"));
        if(client.world==null){finished=true;client.scheduleStop();throw new IllegalStateException(why+" (no world frame available)");}
    }
}
