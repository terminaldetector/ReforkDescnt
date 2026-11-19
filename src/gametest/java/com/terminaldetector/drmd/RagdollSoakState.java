package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.contraption.BlockBodyEntity;
import net.minecraft.nbt.*;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import java.util.*;

/** Test-only durable receipts. They never spawn/restore an entity or replace world storage. */
final class RagdollSoakState extends PersistentState {
    static final int MAX_ROWS=128;
    NbtCompound data=new NbtCompound();
    static RagdollSoakState get(ServerWorld w){return w.getPersistentStateManager().getOrCreate(new Type<>(RagdollSoakState::new,RagdollSoakState::read,null),"drmd_ragdoll_soak_proof");}
    static RagdollSoakState read(NbtCompound tag,RegistryWrapper.WrapperLookup lookup){var s=new RagdollSoakState();s.data=tag.copy();return s;}
    void begin(String source,String mode){if(!data.isEmpty())throw new IllegalStateException("fresh soak receipt required");data.putInt("Schema",1);data.putUuid("Run",UUID.randomUUID());data.putString("Source",source);data.putString("Mode",mode);data.put("Rows",new NbtList());markDirty();}
    void row(NbtCompound row){var rows=data.getList("Rows",NbtElement.COMPOUND_TYPE);if(rows.size()>=MAX_ROWS)throw new IllegalStateException("soak row cap");rows.add(row.copy());data.put("Rows",rows);markDirty();}
    @Override public NbtCompound writeNbt(NbtCompound tag,RegistryWrapper.WrapperLookup lookup){return data.copy();}
    static NbtList material(BlockBodyEntity body){
        var list=new NbtList();body.cells().stream().sorted(Comparator.comparingInt((BlockBodyEntity.Cell c)->c.pos().getX()).thenComparingInt(c->c.pos().getY()).thenComparingInt(c->c.pos().getZ())).forEach(c->{var tag=new NbtCompound();tag.putLong("Pos",c.pos().asLong());tag.put("State",NbtHelper.fromBlockState(c.state()));list.add(tag);});return list;
    }
}
