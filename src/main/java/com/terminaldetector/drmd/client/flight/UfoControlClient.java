package com.terminaldetector.drmd.client.flight;
import com.terminaldetector.drmd.world.mega.UfoControlSystem;
import com.terminaldetector.drmd.client.input.DescentKeybinds;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
public final class UfoControlClient {
    private static boolean active;private static int epoch=-1,sequence;private static double pitch,yaw;
    private UfoControlClient(){}
    public static boolean active(){return active;}
    private static void clear(){active=false;epoch=-1;sequence=0;pitch=yaw=0;}
    public static void mouse(double x,double y){yaw=Math.max(-1,Math.min(1,yaw-x*.01));pitch=Math.max(-1,Math.min(1,pitch-y*.01));}
    public static void register(){
        ClientPlayConnectionEvents.JOIN.register((h,s,c)->clear());ClientPlayConnectionEvents.DISCONNECT.register((h,c)->clear());
        ClientPlayNetworking.registerGlobalReceiver(UfoControlSystem.State.ID,(s,c)->c.client().execute(()->{if(c.client().world==null||!c.client().world.getRegistryKey().getValue().equals(s.dimension())||s.epoch()<epoch)return;active=s.active();epoch=s.epoch();sequence=0;pitch=yaw=0;if(active&&c.player()!=null)c.player().sendMessage(Text.literal("UFO: WASD, Space/Ctrl, Q/E, мышь · Alt — обзор · H — выход"),true);}));
        ClientTickEvents.END_CLIENT_TICK.register(c->{if(!active||c.player==null||!ClientPlayNetworking.canSend(UfoControlSystem.Input.ID))return;
            boolean open=c.currentScreen==null;DescentKeybinds.sampleThrust();float roll=open?(DescentKeybinds.rollRight.isPressed()?1:0)-(DescentKeybinds.rollLeft.isPressed()?1:0):0;
            ClientPlayNetworking.send(new UfoControlSystem.Input(epoch,sequence++,open?DescentKeybinds.inputForward():0,open?DescentKeybinds.inputStrafe():0,open?DescentKeybinds.inputVertical():0,open?(float)pitch:0,open?(float)yaw:0,roll));pitch=yaw=0;
        });
    }
}
