package com.terminaldetector.drmd.gate;

import com.google.gson.GsonBuilder;
import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.client.*;
import com.terminaldetector.drmd.client.config.DescentConfig;
import com.terminaldetector.drmd.client.flight.ShipAttitudeClient;
import com.terminaldetector.drmd.client.input.*;
import com.terminaldetector.drmd.client.presentation.PresentationProfile;
import com.terminaldetector.drmd.client.screen.DroneInventoryScreen;
import com.terminaldetector.drmd.flight.FlightSystem;
import com.terminaldetector.drmd.inventory.*;
import com.terminaldetector.drmd.network.ModNetworking;
import com.terminaldetector.drmd.weapon.items.ModItems;
import com.terminaldetector.drmd.world.access.*;
import com.terminaldetector.drmd.world.edit.WorldEditSafety;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.*;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import net.minecraft.world.LightType;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import java.nio.file.*;
import java.util.*;

/** Actual hotbar/E/N/H/roll and handled-slot routes, live models and completed pixel readback.
 * Declared initial equipment/white background are fixtures, not crafting/firing proof. */
public final class NativeWeaponPresentationCheck {
    public static final boolean ENABLED=Boolean.getBoolean("drmd.gate.weaponPresentation");
    private static final Vec3d START=new Vec3d(96.5,2156,96.5);
    private static final Item[] ITEMS={ModItems.PLASMA,ModItems.ROCKET_DUAL,ModItems.BOMB_CLUSTER};
    private static final List<Map<String,Object>> ROWS=new ArrayList<>(),INPUTS=new ArrayList<>();
    private record View(long tick,boolean nativeWorld,boolean flight,int selected,String hand,List<String> hotbar,List<Integer> hotbarCounts,List<String> banks,List<Integer> bankCounts,String cursor){}
    private record Click(int slot,String cursor,String cell){}
    private static volatile View view;private static volatile boolean setup,background;private static volatile String serverFailure;private static UUID player;
    private static boolean done,sent,selected,rolling;private static long boot,entered,settled;private static int age,phase,profile,frames,previewBank;
    private static final ArrayDeque<Click> clicks=new ArrayDeque<>();private static Click pending;
    private static KeyBinding pressed;private static int pressedAt;
    private static NativeImage flightBackground,beforeHand;
    private static int[] handPixels;
    private static final Set<String> gui=new HashSet<>(),hand=new HashSet<>(),cockpit=new HashSet<>();private static String previewItem;private static int preview=-1;
    private static final List<double[]> projected=new ArrayList<>();private static net.minecraft.client.render.model.json.ModelTransformationMode currentMode;
    private static List<double[]> lastProjection=List.of();private static String frameFailure;
    private NativeWeaponPresentationCheck(){}
    private static String id(ItemStack s){return Registries.ITEM.getId(s.getItem()).toString();}
    private static String expected(int item){return Registries.ITEM.getId(ITEMS[item]).toString();}
    private static void require(boolean yes,String why){if(!yes)throw new IllegalStateException(why);}
    public static void register(){boot=System.nanoTime();ServerTickEvents.END_SERVER_TICK.register(server->{
        if(done||player==null)return;try{var p=server.getPlayerManager().getPlayer(player);if(p==null)return;var w=p.getServerWorld();
            if(!setup){
                p.changeGameMode(GameMode.SURVIVAL);FlightSystem.disable(p,DescentPlayerData.get(p));p.teleport(w,START.x,START.y,START.z,Set.of(),0,0);
                p.setNoGravity(true);p.getAbilities().allowFlying=true;p.getAbilities().flying=true;p.sendAbilitiesUpdate();p.getInventory().clear();DroneLoadout.of(p).clear();
                for(int i=0;i<3;i++)p.getInventory().setStack(i,new ItemStack(ITEMS[i]));p.getInventory().selectedSlot=8;
                p.getInventory().markDirty();p.playerScreenHandler.sendContentUpdates();ModNetworking.syncPlayer(p,DescentPlayerData.get(p));setup=true;
            }
            boolean nativeWorld=WorldAccess.of(w).backend()==CubicAvailability.Backend.NATIVE_CUBIC;
            if(!background&&nativeWorld){var origin=BlockPos.ofFloored(START);var cells=new ArrayList<BlockPos>();
                for(int x=-12;x<=12;x++)for(int y=-10;y<=10;y++)cells.add(origin.add(x,y,6));
                for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)cells.add(origin.add(x,-1,z));
                if(cells.stream().allMatch(at->WorldEditSafety.neighbourhoodLoaded(w,at))){require(cells.size()==574,"fixed authoring budget");
                    for(var at:cells) {
                        var block=at.getY()==origin.getY()-1&&Math.abs(at.getZ()-origin.getZ())<=3
                                ?Blocks.SEA_LANTERN:Blocks.WHITE_CONCRETE;
                        w.setBlockState(at,block.getDefaultState(),3);
                    }
                    background=true;}
            }
            var hotbar=new ArrayList<String>();var hotbarCounts=new ArrayList<Integer>();for(int i=0;i<9;i++){var stack=p.getInventory().getStack(i);hotbar.add(id(stack));hotbarCounts.add(stack.getCount());}var banks=new ArrayList<String>();var bankCounts=new ArrayList<Integer>();var load=DroneLoadout.of(p);for(int i=0;i<5;i++){var stack=load.getStack(i);banks.add(id(stack));bankCounts.add(stack.getCount());}
            view=new View(server.getTicks(),nativeWorld,DescentPlayerData.get(p).isEnabled(),p.getInventory().selectedSlot,id(p.getMainHandStack()),List.copyOf(hotbar),List.copyOf(hotbarCounts),List.copyOf(banks),List.copyOf(bankCounts),id(p.currentScreenHandler.getCursorStack()));
        }catch(Throwable e){serverFailure=e.toString();}});ClientTickEvents.START_CLIENT_TICK.register(NativeWeaponPresentationCheck::tick);}
    private static void enter(int next){phase=next;sent=selected=false;frames=0;settled=0;entered=age;lastProjection=List.of();clicks.clear();pending=null;}
    private static void pulse(KeyBinding key){require(pressed==null,"one physical key pulse at a time");key.setPressed(true);KeyBinding.onKeyPressed(KeyBindingHelper.getBoundKeyOf(key));pressed=key;pressedAt=age;INPUTS.add(Map.of("tick",age,"phase",phase,"binding",key.getTranslationKey(),"down",true));}
    private static boolean hotbar(MinecraftClient c,int slot){if(!selected){pulse(c.options.hotbarKeys[slot]);selected=true;return false;}return c.player.getInventory().selectedSlot==slot&&view.selected==slot;}
    private static boolean flight(MinecraftClient c,boolean enabled){if(view.flight==enabled&&DescentClientState.enabled==enabled)return true;if(!sent){pulse(DescentKeybinds.toggle);sent=true;}return false;}
    private static boolean synchronizedMenu(MinecraftClient c,HandledScreen<?> menu){
        if(!(menu.getScreenHandler() instanceof DroneInventoryHandler)||!id(menu.getScreenHandler().getCursorStack()).equals(view.cursor))return false;
        for(int i=0;i<5;i++){var stack=menu.getScreenHandler().getSlot(i).getStack();if(!id(stack).equals(view.banks.get(i))||stack.getCount()!=view.bankCounts.get(i))return false;}
        for(int i=0;i<9;i++){var stack=menu.getScreenHandler().getSlot(32+i).getStack();if(!id(stack).equals(view.hotbar.get(i))||stack.getCount()!=view.hotbarCounts.get(i))return false;}return true;
    }
    private static void click(HandledScreen<?> menu,int slot){var s=menu.getScreenHandler().getSlot(slot);double x=(menu.width-176)/2.0+s.x+8,y=(menu.height-212)/2.0+s.y+8;menu.mouseClicked(x,y,0);menu.mouseReleased(x,y,0);INPUTS.add(Map.of("tick",age,"phase",phase,"slot",slot));}
    private static void planClick(int slot,String cursor,String cell){clicks.add(new Click(slot,cursor,cell));}
    private static boolean operateMenu(MinecraftClient c){
        if(!(c.currentScreen instanceof DroneInventoryScreen menu))return false;if(!synchronizedMenu(c,menu))return false;
        if(pending!=null){String actual=id(menu.getScreenHandler().getSlot(pending.slot).getStack());if(!actual.equals(pending.cell)||!view.cursor.equals(pending.cursor))return false;pending=null;}
        if(!clicks.isEmpty()){pending=clicks.removeFirst();click(menu,pending.slot);return false;}return true;
    }
    private static void hover(MinecraftClient c,int bank){var menu=(HandledScreen<?>)c.currentScreen;var s=menu.getScreenHandler().getSlot(bank);double x=(menu.width-176)/2.0+s.x+8,y=(menu.height-212)/2.0+s.y+8;
        GLFW.glfwSetCursorPos(c.getWindow().getHandle(),x*c.getWindow().getWidth()/menu.width,y*c.getWindow().getHeight()/menu.height);}
    private static void closeMenu(MinecraftClient c){if(c.currentScreen!=null)c.currentScreen.keyPressed(GLFW.GLFW_KEY_ESCAPE,0,0);}
    private static void tick(MinecraftClient c){
        if(done)return;age++;if(pressed!=null&&age>pressedAt){pressed.setPressed(false);INPUTS.add(Map.of("tick",age,"binding",pressed.getTranslationKey(),"down",false));pressed=null;}
        try{
            if(serverFailure!=null)throw new IllegalStateException(serverFailure);if(age>6400||System.nanoTime()-boot>220_000_000_000L)throw new IllegalStateException("finite220s presentation deadline phase="+phase);
            if(c.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen screen)for(var child:screen.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget b&&b.getMessage().getString().equals("I know what I'm doing!"))b.onPress();
            if(player==null&&c.currentScreen instanceof net.minecraft.client.gui.screen.ConfirmScreen screen&&screen.getTitle().getString().equals("Warning! Low disk space!"))for(var child:screen.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget b&&b.getMessage().getString().equals("Continue"))b.onPress();
            if(c.player==null||c.world==null||c.getServer()==null)return;DescentClient.markUserFlightChoice();
            if(player==null){require(Set.of("cubic-client-automation","weapon-presentationcheck").contains(c.runDirectory.toPath().getFileName().toString()),"disposable native runtime");
                player=c.player.getUuid();c.setScreen(null);c.options.hudHidden=false;c.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);c.options.getGuiScale().setValue(2);c.onResolutionChanged();c.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);c.inGameHud.getChatHud().clear(false);entered=age;return;}
            if(view==null||!background)return;require(view.nativeWorld,"real native cubic backend");require(age-entered<500,"bounded selection/image phase="+phase);
            DescentConfig.presentationProfile=profile==0?PresentationProfile.HYBRID_NATIVE:PresentationProfile.PS1_HYBRID;c.options.getFov().setValue(80);
            if(phase==0){if(!flight(c,false)||!hotbar(c,8))return;if(frames>=3)enter(1);return;}
            if(phase>=1&&phase<=3){if(!hotbar(c,phase-1))return;if(settled==0)settled=age;if(frames>=3)enter(phase+1);return;}
            if(phase==4){if(c.currentScreen==null&&!sent){pulse(c.options.inventoryKey);sent=true;return;}if(frames>=3){closeMenu(c);enter(5);}return;}
            if(phase==5){if(c.currentScreen!=null)return;if(!hotbar(c,8))return;sent=false;enter(6);return;}
            if(phase==6){if(!flight(c,true)||!ShipAttitudeClient.isPrimed())return;double bank=Math.abs(ShipAttitudeClient.get().bankDegrees());
                if(settled==0&&!rolling&&bank>=40)settled=age;
                if(bank<40&&!rolling){DescentKeybinds.rollRight.setPressed(true);rolling=true;INPUTS.add(Map.of("tick",age,"binding",DescentKeybinds.rollRight.getTranslationKey(),"down",true));return;}
                if(rolling&&bank>=40){DescentKeybinds.rollRight.setPressed(false);rolling=false;INPUTS.add(Map.of("tick",age,"binding",DescentKeybinds.rollRight.getTranslationKey(),"down",false));settled=age;}
                if(bank>=35&&age-settled>=8&&frames>=3)enter(7);return;}
            if(phase==7||phase==9||phase==11||phase==13){
                if(c.currentScreen==null){if(!sent){pulse(DescentKeybinds.customize);sent=true;}return;}
                if(!(c.currentScreen instanceof DroneInventoryScreen menu)||!synchronizedMenu(c,menu))return;
                if(!sent)sent=true;
                if(settled==0){
                    require(menu.getScreenHandler().getCursorStack().isEmpty(),"real cursor starts empty");
                    if(phase==7){for(int i=0;i<3;i++){require(view.hotbar.get(i).equals(expected(i))&&view.banks.get(i).equals("minecraft:air"),"declared inventory item and empty actual equipment bank");planClick(32+i,expected(i),"minecraft:air");planClick(i,"minecraft:air",expected(i));}previewBank=0;}
                    else if(phase==9||phase==11){int other=phase==9?1:2;String old=view.banks.get(0),next=view.banks.get(other);planClick(other,next,"minecraft:air");planClick(0,old,next);planClick(other,"minecraft:air",old);previewBank=0;}
                    else{int[] destination={2,0,1};for(int bank=0;bank<3;bank++){String item=view.banks.get(bank);require(view.hotbar.get(destination[bank]).equals("minecraft:air"),"return equipment to its genuine empty source inventory cell");planClick(bank,item,"minecraft:air");planClick(32+destination[bank],"minecraft:air",item);}}
                    settled=age;
                }
                if(!operateMenu(c))return;
                if(phase==13){require(view.banks.subList(0,3).stream().allMatch("minecraft:air"::equals)&&view.bankCounts.subList(0,3).stream().allMatch(n->n==0),"actual sockets empty after ordinary UI return");for(int i=0;i<3;i++)require(view.hotbar.get(i).equals(expected(i))&&view.hotbarCounts.get(i)==1,"all three real item counts conserved");closeMenu(c);
                    if(++profile==2){require(ROWS.stream().filter(r->r.containsKey("png")&&Set.of(1,2,3,8,10,12).contains((Integer)r.get("phase"))).count()==12,"all12 world item/mode/profile scenes have three actual fresh pixel frames");finish(c,"PASS",null);return;}if(flightBackground!=null){flightBackground.close();flightBackground=null;}enter(0);return;}
                hover(c,previewBank);
                if(frames>=3){if(phase==7&&previewBank<2){previewBank++;frames=0;return;}closeMenu(c);enter(phase+1);}return;
            }
            if(phase==8||phase==10||phase==12){require(c.player.getMainHandStack().isEmpty()&&view.hand.equals("minecraft:air")&&DescentClientState.enabled&&view.flight,"real empty-hand socket view");if(settled==0)settled=age;if(frames>=3)enter(phase+1);}
        }catch(Throwable e){finish(c,"FAIL",e.toString());}
    }
    public static void beforeFrame(){if(!ENABLED)return;gui.clear();hand.clear();cockpit.clear();projected.clear();previewItem=null;preview=-1;handPixels=null;
        if(beforeHand!=null){beforeHand.close();beforeHand=null;}}
    public static void beforeHand(){
        if(!ENABLED||done||phase<1||phase>3||settled==0||age-settled<8||view==null
                ||!view.hand.equals(expected(phase-1))||com.terminaldetector.drmd.client.portal.OffscreenWorldView.busy())return;
        var c=MinecraftClient.getInstance();
        if(c.currentScreen==null&&c.getOverlay()==null&&beforeHand==null)beforeHand=capture(c);
    }
    public static void afterHand(){
        if(beforeHand==null||com.terminaldetector.drmd.client.portal.OffscreenWorldView.busy())return;
        try(var before=beforeHand;var after=capture(MinecraftClient.getInstance())){handPixels=difference(after,before);}
        finally{beforeHand=null;}
    }
    public static void beginModel(net.minecraft.client.render.model.json.ModelTransformationMode mode){if(ENABLED)currentMode=mode;}
    private static void project(net.minecraft.client.util.math.MatrixStack matrices,List<org.joml.Vector3f> points){
        if(projected.size()>=256){frameFailure="bounded256 actual projected mesh regions overflow";return;}var c=MinecraftClient.getInstance();double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX;
        for(var v:points){var p=new org.joml.Vector4f(v.x,v.y,v.z,1).mul(matrices.peek().getPositionMatrix()).mul(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix()).mul(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix());
            if(p.w<=1e-6||!Float.isFinite(p.w))continue;double x=(p.x/p.w*.5+.5)*c.getWindow().getFramebufferWidth(),y=(.5-p.y/p.w*.5)*c.getWindow().getFramebufferHeight();minX=Math.min(minX,x);minY=Math.min(minY,y);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);}
        if(Double.isFinite(minX+minY+maxX+maxY))projected.add(new double[]{minX,minY,maxX,maxY});
    }
    public static void baked(net.minecraft.client.render.model.BakedModel model,ItemStack stack,net.minecraft.client.util.math.MatrixStack matrices){
        if(!ENABLED||done||currentMode==null||currentMode==net.minecraft.client.render.model.json.ModelTransformationMode.GUI||Arrays.stream(ITEMS).noneMatch(stack::isOf))return;
        if(currentMode!=net.minecraft.client.render.model.json.ModelTransformationMode.FIRST_PERSON_RIGHT_HAND&&currentMode!=net.minecraft.client.render.model.json.ModelTransformationMode.FIRST_PERSON_LEFT_HAND)return;
        var random=net.minecraft.util.math.random.Random.create(42);var sides=new ArrayList<Direction>(Arrays.asList(Direction.values()));sides.add(null);int count=0;var vertices=new ArrayList<org.joml.Vector3f>();
        for(var side:sides){random.setSeed(42);for(var quad:model.getQuads(null,side,random)){if(++count>1024){frameFailure="bounded1024 actual item model quads overflow";return;}var data=quad.getVertexData();int stride=data.length/4;
            for(int i=0;i<4;i++)vertices.add(new org.joml.Vector3f(Float.intBitsToFloat(data[i*stride]),Float.intBitsToFloat(data[i*stride+1]),Float.intBitsToFloat(data[i*stride+2])));}}
        project(matrices,vertices);
    }
    public static void model(ItemStack stack,net.minecraft.client.render.model.json.ModelTransformationMode mode,boolean missing){
        if(!ENABLED||done)return;String id=id(stack);if(Arrays.stream(ITEMS).noneMatch(item->stack.isOf(item)))return;
        if(missing){frameFailure="actual item renderer selected missing baked model: "+id;return;}
        if(mode==net.minecraft.client.render.model.json.ModelTransformationMode.GUI)gui.add(id);
        if(mode==net.minecraft.client.render.model.json.ModelTransformationMode.FIRST_PERSON_RIGHT_HAND||mode==net.minecraft.client.render.model.json.ModelTransformationMode.FIRST_PERSON_LEFT_HAND)hand.add(id);
    }
    private static boolean cockpitLive(){var c=MinecraftClient.getInstance();return ENABLED&&!done&&c.player!=null&&c.currentScreen==null&&DescentClientState.enabled&&!com.terminaldetector.drmd.client.portal.OffscreenWorldView.busy();}
    public static void cockpitBox(net.minecraft.client.util.math.MatrixStack matrices){if(!cockpitLive())return;cockpit.add(id(DroneCombatInput.primary(MinecraftClient.getInstance())));var vertices=new ArrayList<org.joml.Vector3f>();for(int i=0;i<8;i++)vertices.add(new org.joml.Vector3f((i&1)==0?-.5f:.5f,(i&2)==0?-.5f:.5f,(i&4)==0?-.5f:.5f));project(matrices,vertices);}
    public static void cockpitMesh(net.minecraft.client.util.math.MatrixStack matrices,com.terminaldetector.drmd.workshop.ClusterModule module,float recoil){if(!cockpitLive())return;
        var selected=DroneCombatInput.primary(MinecraftClient.getInstance());if(!(selected.getItem() instanceof com.terminaldetector.drmd.weapon.items.DescentWeaponItem item)||!com.terminaldetector.drmd.workshop.ConstructionRegistry.getModules(item.getDef().id).contains(module))return;
        cockpit.add(id(selected));int count=0;for(var part:com.terminaldetector.drmd.workshop.HybridWeaponModels.parts(module.model)){
            if(++count>64||part.faces().size()>128){frameFailure="bounded64 parts/128faces per real cockpit part overflow";return;}var vertices=part.faces().stream().flatMap(f->f.points().stream()).map(p->com.terminaldetector.drmd.workshop.HybridWeaponModels.transform(module,p,recoil,part.moving()).div(16)).toList();project(matrices,vertices);}
    }
    public static void preview(ItemStack selected,int bank){if(ENABLED&&!done){previewItem=id(selected);preview=bank;}}
    private static int channel(int colour,int shift){return colour>>>shift&255;}
    private static int delta(int a,int b){return Math.max(Math.abs(channel(a,0)-channel(b,0)),Math.max(Math.abs(channel(a,8)-channel(b,8)),Math.abs(channel(a,16)-channel(b,16))));}
    private static int[] difference(NativeImage image,NativeImage base){int minX=image.getWidth(),minY=image.getHeight(),maxX=-1,maxY=-1,count=0;
        require(base!=null&&base.getWidth()==image.getWidth()&&base.getHeight()==image.getHeight(),"fresh same-size actual empty-hand background");
        for(int x=image.getWidth()*15/100;x<image.getWidth()*98/100;x+=2)for(int y=image.getHeight()*35/100;y<image.getHeight()*87/100;y+=2){boolean inside=false;for(var box:projected)if(x+.5>=box[0]-1&&x+.5<=box[2]+1&&y+.5>=box[1]-1&&y+.5<=box[3]+1){inside=true;break;}
            if(inside&&delta(image.getColor(x,y),base.getColor(x,y))>=32){count++;minX=Math.min(minX,x);minY=Math.min(minY,y);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);}}
        return new int[]{count,minX,minY,maxX,maxY};
    }
    private static double projectedShift(){
        if(lastProjection.isEmpty())return 0;
        if(projected.size()!=lastProjection.size())return Double.POSITIVE_INFINITY;
        double maximum=0;
        for(int i=0;i<projected.size();i++){
            var old=lastProjection.get(i);var next=projected.get(i);
            maximum=Math.max(maximum,Math.hypot((next[0]+next[2]-old[0]-old[2])*.5,(next[1]+next[3]-old[1]-old[3])*.5));
        }
        return maximum;
    }
    private static int icon(NativeImage image,int x,int y,MinecraftClient c){double sx=(double)image.getWidth()/c.getWindow().getScaledWidth(),sy=(double)image.getHeight()/c.getWindow().getScaledHeight();int atX=(int)((x+1)*sx),atY=(int)((y+1)*sy),background=image.getColor(Math.min(image.getWidth()-1,atX),Math.min(image.getHeight()-1,atY));int count=0;
        for(int dx=2;dx<14;dx++)for(int dy=2;dy<14;dy++){int px=(int)((x+dx)*sx),py=(int)((y+dy)*sy);if(px>=0&&py>=0&&px<image.getWidth()&&py<image.getHeight()&&delta(image.getColor(px,py),background)>=28)count++;}return count;}
    private static NativeImage capture(MinecraftClient c){
        int active=GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE),texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int[] names={GL11.GL_PACK_ALIGNMENT,GL11.GL_PACK_ROW_LENGTH,GL11.GL_PACK_SKIP_ROWS,GL11.GL_PACK_SKIP_PIXELS},values=new int[4];for(int i=0;i<4;i++)values[i]=GL11.glGetInteger(names[i]);
        try{for(int i=0;i<4;i++)GL11.glPixelStorei(names[i],i==0?4:0);return ScreenshotRecorder.takeScreenshot(c.getFramebuffer());}
        finally{com.mojang.blaze3d.platform.GlStateManager._activeTexture(active);com.mojang.blaze3d.systems.RenderSystem.bindTexture(texture);for(int i=0;i<4;i++)GL11.glPixelStorei(names[i],values[i]);}
    }
    public static void rendered(){
        if(!ENABLED||done)return;var c=MinecraftClient.getInstance();if(c.player==null||c.world==null||view==null||!background||c.getOverlay()!=null)return;
        try{
            int light = c.world.getLightLevel(LightType.BLOCK, BlockPos.ofFloored(c.player.getEyePos()));
            if (light < 11) return; // Ordinary block light must actually reach the physical viewmodel fixture.
            if(frameFailure!=null)throw new IllegalStateException(frameFailure);boolean empty=phase==0||phase==6;boolean ordinary=phase>=1&&phase<=3,flight=phase==8||phase==10||phase==12;
            boolean inventory=phase==4&&c.currentScreen instanceof InventoryScreen;boolean equipment=(phase==7||phase==9||phase==11)&&c.currentScreen instanceof DroneInventoryScreen&&pending==null&&clicks.isEmpty()&&settled>0;
            if(!empty&&!ordinary&&!flight&&!inventory&&!equipment)return;if(empty){if(c.currentScreen!=null||!c.player.getMainHandStack().isEmpty()||!view.hand.equals("minecraft:air"))return;if(phase==0&&(DescentClientState.enabled||view.flight))return;if(phase==6&&(!DescentClientState.enabled||!view.flight||rolling||Math.abs(ShipAttitudeClient.get().bankDegrees())<35||age-settled<8))return;}
            if(ordinary&&(!view.hand.equals(expected(phase-1))||!hand.contains(expected(phase-1))||handPixels==null||age-settled<8||c.currentScreen!=null))return;
            int current=flight?(phase-8)/2:-1;if(flight&&(!view.banks.get(0).equals(expected(current))||!cockpit.contains(expected(current))||age-settled<8||c.currentScreen!=null))return;
            if(equipment&&(preview!=previewBank||!Objects.equals(previewItem,view.banks.get(previewBank))))return;
            require(GL11.glGetError()==0,"pre-readback GL state");
            try(var image=capture(c)){
                String name="profile"+profile+"-phase"+phase+(equipment?"-bank"+previewBank:"");var row=new LinkedHashMap<String,Object>();row.put("profile",DescentConfig.presentationProfile.name());row.put("phase",phase);row.put("bank",previewBank);row.put("frame",frames);row.put("serverTick",view.tick);row.put("hand",view.hand);row.put("banks",view.banks);row.put("heldModels",Set.copyOf(hand));row.put("cockpitModels",Set.copyOf(cockpit));row.put("guiModels",Set.copyOf(gui));row.put("preview",previewItem);row.put("roll",DescentClientState.enabled?ShipAttitudeClient.get().bankDegrees():0);row.put("blockLightAtEye",light);
                if(phase==6){var copy=new NativeImage(image.getWidth(),image.getHeight(),false);copy.copyFrom(image);if(flightBackground!=null)flightBackground.close();flightBackground=copy;}
                if(ordinary||flight){var pixels=ordinary?handPixels:difference(image,flightBackground);double shift=projectedShift();
                    row.put("foreground",pixels);row.put("actualProjectedModelRegions",List.copyOf(projected));row.put("anchorShiftPixels",Double.isFinite(shift)?shift:null);
                    row.put("pixelWitness",ordinary?"same frame around native hand draw":"matched empty cockpit frame");
                    if(pixels[0]<12||shift>2||projected.isEmpty()){Path directory=c.runDirectory.toPath().resolve("weapon-presentation-frames");Files.createDirectories(directory);
                        String invalid=name+"-failed-frame"+frames+".png";image.writeTo(directory.resolve(invalid));row.put("png","weapon-presentation-frames/"+invalid);ROWS.add(row);
                        require(!projected.isEmpty(),"actual selected model submits projected geometry");
                        require(pixels[0]>=12,"actual selected model produces foreground pixels in its world viewport");require(shift<=2,"three fresh stationary model frames keep the same screen anchor: "+shift+" pixels");}
                    lastProjection=List.copyOf(projected);}
                if(inventory||equipment){var menu=(HandledScreen<?>)c.currentScreen;int x=(menu.width-176)/2,y=(menu.height-(equipment?212:166))/2;var counts=new LinkedHashMap<String,Integer>();
                    for(var slot:menu.getScreenHandler().slots){String item=id(slot.getStack());if(Arrays.stream(ITEMS).noneMatch(it->slot.getStack().isOf(it)))continue;require(gui.contains(item),"actual GUI draws a nonmissing item model");int pixels=icon(image,x+slot.x,y+slot.y,c);require(pixels>=6,"actual item-icon ROI contains visible sprite pixels for "+item);counts.put(item,pixels);}
                    require(counts.size()==3,"all three actual representative item icons are visible and synchronized");row.put("icons",counts);if(equipment){int content=icon(image,x+82,y+42,c);row.put("previewProbePixels",content);}}
                require(ROWS.size()<256,"finite image/metric retention");ROWS.add(row);frames++;if(frames==3){Path directory=c.runDirectory.toPath().resolve("weapon-presentation-frames");Files.createDirectories(directory);String png=name+".png";image.writeTo(directory.resolve(png));row.put("png","weapon-presentation-frames/"+png);}
            }
            require(GL11.glGetError()==0,"completed final framebuffer readback");
        }catch(Throwable e){finish(c,"FAIL",e.toString());}
    }
    private static void finish(MinecraftClient c,String status,String failure){
        if(done)return;done=true;if(pressed!=null)pressed.setPressed(false);if(DescentKeybinds.rollRight!=null)DescentKeybinds.rollRight.setPressed(false);
        try{var result=new LinkedHashMap<String,Object>();result.put("status",status);result.put("failure",failure);result.put("phase",phase);result.put("profile",profile);result.put("rows",ROWS);result.put("inputs",INPUTS);result.put("serverView",view);result.put("sourceBinding",System.getProperty("drmd.gate.weaponPresentationSource","UNBOUND"));result.put("glRenderer",GL11.glGetString(GL11.GL_RENDERER));result.put("scope","3 representative registered items; ordinary+empty-hand6DoF primary, real hotbar/E/N/H/roll and slot packets, Native+PS1 atFOV80; no new firing/per-ID/FOV/mounted matrix claim");
            Files.writeString(c.runDirectory.toPath().resolve("weapon-presentation-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result)+"\n");
        }catch(Exception e){e.printStackTrace();}finally{if(beforeHand!=null)beforeHand.close();if(flightBackground!=null)flightBackground.close();}System.out.println("WEAPON_PRESENTATION_OUTCOME "+status+" "+failure);c.scheduleStop();
    }
}
