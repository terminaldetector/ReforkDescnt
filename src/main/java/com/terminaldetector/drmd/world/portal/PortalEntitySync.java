package com.terminaldetector.drmd.world.portal;

import com.terminaldetector.drmd.entity.RagdollEntity;
import com.terminaldetector.drmd.world.contraption.BlockBodyEntity;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.*;

/** Emit ordinary entity snapshots after physical commit, before an occupant's absolute transfer.
 * Never drains tracker dirty entries or changes tracker membership/chunk readiness. */
final class PortalEntitySync {
    private PortalEntitySync() {}
    static void publish(List<Entity> group){
        var occupants=group.stream().filter(ServerPlayerEntity.class::isInstance).map(ServerPlayerEntity.class::cast).toList();
        for(var e:group){
            if(!(e instanceof BlockBodyEntity)&&!(e instanceof RagdollEntity))continue;
            var tracking=new LinkedHashSet<>(PlayerLookup.tracking(e));var recipients=new LinkedHashSet<>(tracking);recipients.addAll(occupants);
            var pose=e instanceof BlockBodyEntity b?b.portalTrackedPose():((RagdollEntity)e).portalTrackedPose();
            var position=new EntityPositionS2CPacket(e);var update=new EntityTrackerUpdateS2CPacket(e.getId(),pose);
            EntitySpawnS2CPacket spawn=null;EntityTrackerUpdateS2CPacket initial=null;
            for(var recipient:recipients){
                if(recipient.networkHandler==null||recipient.isRemoved()||recipient.getWorld()!=e.getWorld())continue;
                // A native cubic pivot may be absent from vanilla's tracker. Its actual occupants
                // still need the existing physical identity and real shape, not an unknown-ID update.
                if(!tracking.contains(recipient)){
                    if(spawn==null){spawn=new EntitySpawnS2CPacket(e.getId(),e.getUuid(),e.getX(),e.getY(),e.getZ(),e.getPitch(),e.getYaw(),e.getType(),0,e.getVelocity(),e.getHeadYaw());
                        var values=e.getDataTracker().getChangedEntries();if(values!=null)initial=new EntityTrackerUpdateS2CPacket(e.getId(),values);}
                    recipient.networkHandler.sendPacket(spawn);if(initial!=null)recipient.networkHandler.sendPacket(initial);
                }
                recipient.networkHandler.sendPacket(position);recipient.networkHandler.sendPacket(update);
            }
        }
    }
}
