package com.terminaldetector.drmd.client.flight;
import com.terminaldetector.drmd.world.mega.UfoControlSystem;
import com.terminaldetector.drmd.world.mega.ControlStateRules;
import com.terminaldetector.drmd.client.input.DescentKeybinds;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
public final class UfoControlClient {
    private static boolean active;private static int epoch=-1,sequence,fireSequence,turretSequence;private static double pitch,yaw;
    private static Identifier dimension;
    private static ControlStateRules.Update accepted;
    private UfoControlClient(){}
    public static boolean active(){return active;}
    private static void clear(){active=false;epoch=-1;sequence=0;fireSequence=0;turretSequence=0;pitch=yaw=0;dimension=null;accepted=null;}
    private static void deactivate(){active=false;pitch=yaw=0;if(accepted!=null)accepted=accepted.released();}
    private static void apply(MinecraftClient c,ControlStateRules.Update next){
        if(c.world==null||!ControlStateRules.admit(accepted,next,c.world.getRegistryKey().getValue().toString())
            ||next.active()&&(c.player==null||!c.player.isAlive()||c.player.isSpectator()))return;
        accepted=next;active=next.active();epoch=next.epoch();dimension=Identifier.of(next.dimension());
        sequence=0;fireSequence=0;turretSequence=0;pitch=yaw=0;
        if(active&&c.player!=null)c.player.sendMessage(Text.literal(next.kind()==ControlStateRules.Kind.CORE
            ?"Корабль: WASD, Space/Ctrl, Q/E, мышь · ЛКМ — лазеры · ПКМ — ракеты · Alt — обзор · H — выход"
            :"UFO: WASD, Space/Ctrl, Q/E, мышь · ЛКМ — лазеры · ПКМ — ракеты · ←/→ — турель · B — залп · Alt — обзор · H — выход"),true);
    }
    public static void mouse(double x,double y){yaw=Math.max(-1,Math.min(1,yaw-x*.01));pitch=Math.max(-1,Math.min(1,pitch-y*.01));}
    public static void register(){
        ClientPlayConnectionEvents.JOIN.register((h,s,c)->clear());ClientPlayConnectionEvents.DISCONNECT.register((h,c)->clear());
        ClientPlayNetworking.registerGlobalReceiver(UfoControlSystem.State.ID,(s,c)->{
            var handler=c.client().getNetworkHandler();var owner=c.player();c.client().execute(()->{
                if(handler!=c.client().getNetworkHandler()||owner!=c.client().player||s.epoch()<0)return;
                apply(c.client(),new ControlStateRules.Update(s.body(),s.epoch(),s.active(),s.dimension().toString(),ControlStateRules.Kind.SKY,7,true));
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(UfoControlSystem.CoreState.ID,(s,c)->{
            var handler=c.client().getNetworkHandler();var owner=c.player();c.client().execute(()->{
                if(handler!=c.client().getNetworkHandler()||owner!=c.client().player)return;
                apply(c.client(),new ControlStateRules.Update(s.body(),s.epoch(),s.active(),s.dimension().toString(),ControlStateRules.Kind.CORE,s.fireMask(),s.turret()));
            });
        });
        ClientTickEvents.END_CLIENT_TICK.register(c->{
            // A dimension/death transition can replace the local world before the old
            // world's release packet arrives. Never retain mouse ownership across it.
            if(active&&(c.world==null||c.player==null||!c.player.isAlive()||c.player.isSpectator()||!c.world.getRegistryKey().getValue().equals(dimension))){deactivate();return;}
            if(!active||c.player==null||c.isPaused()||!ClientPlayNetworking.canSend(UfoControlSystem.Input.ID))return;
            boolean open=c.currentScreen==null;DescentKeybinds.sampleThrust();float roll=open?(DescentKeybinds.rollRight.isPressed()?1:0)-(DescentKeybinds.rollLeft.isPressed()?1:0):0;
            ClientPlayNetworking.send(new UfoControlSystem.Input(epoch,sequence++,open?DescentKeybinds.inputForward():0,open?DescentKeybinds.inputStrafe():0,open?DescentKeybinds.inputVertical():0,open?(float)pitch:0,open?(float)yaw:0,roll));pitch=yaw=0;
            int buttons=open?(c.options.attackKey.isPressed()?1:0)|(c.options.useKey.isPressed()?2:0)|(DescentKeybinds.auxiliary.isPressed()?4:0):0;
            if(ClientPlayNetworking.canSend(UfoControlSystem.Fire.ID))ClientPlayNetworking.send(new UfoControlSystem.Fire(epoch,fireSequence++,ControlStateRules.sampleFireMask(accepted,buttons)));
            if(ControlStateRules.sendTurret(accepted)&&ClientPlayNetworking.canSend(UfoControlSystem.TurretInput.ID))ClientPlayNetworking.send(new UfoControlSystem.TurretInput(epoch,turretSequence++,open?(DescentKeybinds.turretRight.isPressed()?1:0)-(DescentKeybinds.turretLeft.isPressed()?1:0):0));
        });
    }
}
