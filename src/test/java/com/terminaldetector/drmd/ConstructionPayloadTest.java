package com.terminaldetector.drmd;

import com.terminaldetector.drmd.network.ModNetworking;
import com.terminaldetector.drmd.workshop.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.nbt.NbtList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConstructionPayloadTest {
    @Test void constructionPacketRoundTripsPopulatedAndResetLayouts() {
        for(var modules:java.util.List.of(ClusterModule.listToNbt(HybridWeaponModels.sample(HybridWeaponModels.RAIL)),new NbtList())) {
            var buffer=new RegistryByteBuf(Unpooled.buffer(),DynamicRegistryManager.EMPTY);
            try {
                var input=new ModNetworking.ConstructionPayload("gravy_railgun",modules);
                ModNetworking.ConstructionPayload.CODEC.encode(buffer,input);
                var output=ModNetworking.ConstructionPayload.CODEC.decode(buffer);
                assertEquals(input.weaponId(),output.weaponId()); assertEquals(modules,output.modules());
                assertEquals(0,buffer.readableBytes());
            }finally {buffer.release();}
        }
    }
}
