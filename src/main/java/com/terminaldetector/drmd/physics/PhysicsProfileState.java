package com.terminaldetector.drmd.physics;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

/** One authoritative, persisted profile per save, shared across its dimensions. */
public final class PhysicsProfileState extends PersistentState {
    private PhysicsProfile profile=com.terminaldetector.drmd.world.DrmdServerConfig.physicsProfile;
    public PhysicsProfileState() {markDirty();}
    public static PhysicsProfileState get(ServerWorld world) {
        return world.getServer().getOverworld().getPersistentStateManager().getOrCreate(
                new Type<>(PhysicsProfileState::new,PhysicsProfileState::fromNbt,null),"drmd_physics");
    }
    public static PhysicsProfileState fromNbt(NbtCompound nbt,RegistryWrapper.WrapperLookup lookup) {
        var state=new PhysicsProfileState();state.profile=PhysicsProfile.parse(nbt.getString("Profile"));return state;
    }
    @Override public NbtCompound writeNbt(NbtCompound nbt,RegistryWrapper.WrapperLookup lookup) {
        nbt.putInt("Schema",1);nbt.putString("Profile",profile.name());return nbt;
    }
    public PhysicsProfile profile() {return profile;}
    public void set(PhysicsProfile value) {if(profile!=value){profile=value;markDirty();}}
    public static boolean expanded(ServerWorld world) {return get(world).profile==PhysicsProfile.EXPANDED;}
    public static void sync(ServerPlayerEntity player) {
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new com.terminaldetector.drmd.network.ModNetworking.PhysicsProfilePayload(get(player.getServerWorld()).profile.name(),player.hasPermissionLevel(2)));
    }
    public static void request(ServerPlayerEntity player,PhysicsProfile desired) {
        if(!player.hasPermissionLevel(2)){sync(player);return;}
        get(player.getServerWorld()).set(desired);
        for(var online:player.getServer().getPlayerManager().getPlayerList())sync(online);
    }
}
