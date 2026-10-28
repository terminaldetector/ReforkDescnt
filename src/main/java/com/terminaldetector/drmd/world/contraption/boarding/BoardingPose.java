package com.terminaldetector.drmd.world.contraption.boarding;

import com.terminaldetector.drmd.client.portal.PortalTransform.*;
import com.terminaldetector.drmd.physics.collision.*;
import net.minecraft.entity.player.PlayerEntity;
import java.util.*;

/** One frame owner for local geometry. Per-move overrides do not become a passenger position anchor. */
public final class BoardingPose {
    public interface Access {
        Quat drmd$boardingFrame();
        boolean drmd$boardingPoseActive();
        void drmd$setBoardingFrame(Quat frame,boolean active);
        boolean drmd$boardingFrameData(net.minecraft.entity.data.TrackedData<?> data);
    }
    private record MoveFrame(PlayerEntity player,Quat frame) {}
    private static final ThreadLocal<MoveFrame> MOVING=new ThreadLocal<>();
    private static final Map<PlayerEntity,LocalPose> PREDICTED=Collections.synchronizedMap(new WeakHashMap<>());
    private record LocalPose(Quat frame,boolean active) {}
    private BoardingPose() {}
    public static boolean active(PlayerEntity player) {
        var moving=MOVING.get();if(moving!=null&&moving.player==player)return true;
        var local=PREDICTED.get(player);if(local!=null)return local.active();
        return player instanceof Access a&&a.drmd$boardingPoseActive();
    }
    public static Quat frame(PlayerEntity player) {
        var moving=MOVING.get();if(moving!=null&&moving.player==player)return moving.frame;
        var predicted=PREDICTED.get(player);if(predicted!=null)return predicted.frame();
        return player instanceof Access a?a.drmd$boardingFrame():Quat.IDENTITY;
    }
    public static boolean checkedMove(PlayerEntity player){var moving=MOVING.get();return moving!=null&&moving.player==player;}
    public static void publish(PlayerEntity player,Quat frame) {
        if(player.getWorld().isClient)PREDICTED.put(player,new LocalPose(frame,true));
        else if(player instanceof Access access)access.drmd$setBoardingFrame(frame,true);
        player.calculateDimensions();
    }
    public static void clear(PlayerEntity player) {
        if(player.getWorld().isClient)PREDICTED.put(player,new LocalPose(Quat.IDENTITY,false));else PREDICTED.remove(player);
        if(!player.getWorld().isClient&&player instanceof Access a)a.drmd$setBoardingFrame(Quat.IDENTITY,false);
        player.calculateDimensions();
    }
    public static void clearClient(){PREDICTED.clear();}
    public static void move(PlayerEntity player,Quat frame,Runnable movement) {
        var before=MOVING.get();MOVING.set(new MoveFrame(player,frame));
        try {player.calculateDimensions();movement.run();}
        finally {if(before==null)MOVING.remove();else MOVING.set(before);}
    }
}
