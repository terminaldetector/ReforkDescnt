package com.terminaldetector.drmd.world.portal.mirror;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.Registries;
import java.util.UUID;

/** Retire only the saved external entity explicitly owned by this block. No external API. */
public final class LegacyPortalEntities {
    private LegacyPortalEntities() {}
    public static void detach(ServerWorld world, UUID id) {
        if (id == null) return;
        var entity = world.getEntity(id);
        if (entity == null) return; // never load a chunk to find an old attachment
        String namespace = Registries.ENTITY_TYPE.getId(entity.getType()).getNamespace();
        if (namespace.equals("imm_ptl") || namespace.equals("immersive_portals")) entity.discard();
    }
}
