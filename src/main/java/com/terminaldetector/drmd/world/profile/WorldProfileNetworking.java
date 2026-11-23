package com.terminaldetector.drmd.world.profile;

import com.terminaldetector.drmd.DescentMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class WorldProfileNetworking {
    private static volatile WorldProfile clientProfile;

    private WorldProfileNetworking() {}

    public static void register() {
        PayloadTypeRegistry.playS2C().register(ProfilePayload.ID, ProfilePayload.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            WorldProfile profile = WorldProfile.get(handler.player.getServerWorld());
            if (profile != null && ServerPlayNetworking.canSend(handler.player, ProfilePayload.ID)) {
                var encoded = WorldProfile.CODEC.encodeStart(NbtOps.INSTANCE, profile).getOrThrow();
                ServerPlayNetworking.send(handler.player, new ProfilePayload((NbtCompound) encoded));
            }
        });
    }

    public static WorldProfile clientProfile() { return clientProfile; }

    public static void acceptServerProfile(NbtCompound nbt) {
        clientProfile = nbt == null ? null : WorldProfile.CODEC.parse(NbtOps.INSTANCE, nbt).result().orElse(null);
    }

    public record ProfilePayload(NbtCompound profile) implements CustomPayload {
        public static final Id<ProfilePayload> ID = new Id<>(Identifier.of(DescentMod.MOD_ID, "world_profile"));
        public static final PacketCodec<RegistryByteBuf, ProfilePayload> CODEC = PacketCodec.of(
                (payload, buf) -> buf.writeNbt(payload.profile), buf -> new ProfilePayload(buf.readNbt()));

        @Override public Id<ProfilePayload> getId() { return ID; }
    }
}
