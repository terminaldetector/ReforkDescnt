package com.terminaldetector.drmd.world.level;

import com.terminaldetector.drmd.DescentPlayerData;
import com.terminaldetector.drmd.diag.DiagTrace;
import com.terminaldetector.drmd.entity.PyroShipEntity;
import com.terminaldetector.drmd.flight.FlightSystem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Emergency recovery before vanilla void damage, only in the authored tall Overworld. */
public final class LowerWorldRecovery {
    private record SafePoint(ServerWorld world,Vec3d position) {}
    private static final Map<UUID,SafePoint> SAFE=new ConcurrentHashMap<>();
    private LowerWorldRecovery() {}
    public static void clear(UUID id) { SAFE.remove(id); }
    public static void clearAll() { SAFE.clear(); }
    public static void tick(MinecraftServer server) {
        for(var player:server.getPlayerManager().getPlayerList()) {
            if(player.getWorld().getRegistryKey()==World.OVERWORLD && WorldLevels.isAdvancedColumn(player.getServerWorld()))
                recoverOrRemember(player);
        }
    }
    /** Public for the world regression; the production caller applies the advanced-world scope. */
    public static boolean recoverOrRemember(ServerPlayerEntity player) {
        if(!player.isAlive() || player.isSpectator()) return false;
        ServerWorld world=player.getServerWorld(); double y=player.getY();
        if(!VerticalBounds.needsLowerRecovery(y,world.getBottomY())) {
            if(y>=world.getBottomY()+64 && VerticalBounds.contains(y,world.getBottomY(),world.getTopY()-3)
                    && world.isSpaceEmpty(player,player.getBoundingBox()) && world.getFluidState(player.getBlockPos()).isEmpty())
                SAFE.put(player.getUuid(),new SafePoint(world,player.getPos()));
            return false;
        }
        SafePoint remembered=SAFE.get(player.getUuid());
        Vec3d destination=remembered!=null && remembered.world()==world?remembered.position():null;
        if(destination==null || !world.isSpaceEmpty(player,com.terminaldetector.drmd.world.gravity.GravityMount.standingBox(destination,new Vec3d(0,1,0),player.getWidth()/2,player.getDimensions(net.minecraft.entity.EntityPose.STANDING).height()))) {
            var spawn=world.getSpawnPos();
            destination=new Vec3d(spawn.getX()+.5,Math.max(world.getBottomY()+8,
                    world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING,spawn.getX(),spawn.getZ())+1),spawn.getZ()+.5);
        }
        var data=DescentPlayerData.get(player);
        if(player.getVehicle() instanceof PyroShipEntity ship) {
            ship.setPosition(destination); ship.setVelocity(Vec3d.ZERO);
            data.setLastShip(ship.getUuid(),ship.getBlockPos());
        }
        FlightSystem.disable(player,data);
        player.requestTeleport(destination.x,destination.y,destination.z);
        player.setVelocity(Vec3d.ZERO); player.fallDistance=0;
        FlightSystem.enableWithoutMount(player,data);
        player.sendMessage(Text.translatable("message.drmd.lower_world_recovery"),true);
        DiagTrace.record("boundary","Recovered player "+player.getUuid()+" from Y="+y+" to "+destination);
        return true;
    }
}
