package com.terminaldetector.drmd.gate;

import com.terminaldetector.drmd.world.access.*;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import java.nio.file.*;
import java.util.List;

/** Actual BCC state/NBT ownership edits at the Y2159/2160 native cube boundary. */
public final class NativeWorldEditsGate {
    private static final BlockPos A=new BlockPos(12,2159,12),B=new BlockPos(12,2160,12);
    private NativeWorldEditsGate() {}
    private static ServerBlockEdits.Cell air(){return new ServerBlockEdits.Cell(Blocks.AIR.getDefaultState(),null);}
    private static ServerBlockEdits.Cell iron(){return new ServerBlockEdits.Cell(Blocks.IRON_BLOCK.getDefaultState(),null);}
    public static void verify(ServerWorld world,Path root) {
        var access=WorldAccess.of(world);boolean reopen=Boolean.getBoolean("drmd.gate.worldEdits.reopen");
        int drops=world.getEntitiesByClass(ItemEntity.class,new Box(A).union(new Box(B)).expand(.25),e->true).size();
        if(!reopen) {
            if(!access.snapshotIfLoaded(A).orElseThrow().equals(air()) || !access.snapshotIfLoaded(B).orElseThrow().equals(air()))
                throw new IllegalStateException("Native edit fixture is not fresh air at both coordinates");
            var template=new ChestBlockEntity(A,Blocks.CHEST.getDefaultState());
            var named=new ItemStack(Items.CHEST);named.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,Text.literal("cubic-moved-37"));
            template.readComponents(named);template.setStack(0,new ItemStack(Items.DIAMOND,37));
            var created=new ServerBlockEdits.Cell(Blocks.CHEST.getDefaultState(),template.createNbtWithIdentifyingData(world.getRegistryManager()));
            require(access.modifyServerAuthoritative(List.of(new ServerBlockEdits.Edit(A,air(),created))).committed(),"create source chest with real NBT");
            var before=access.snapshotIfLoaded(A).orElseThrow();var relocated=before.entityData();
            relocated.putInt("x",B.getX());relocated.putInt("y",B.getY());relocated.putInt("z",B.getZ());
            var moved=access.modifyServerAuthoritative(List.of(new ServerBlockEdits.Edit(A,before,air()),
                new ServerBlockEdits.Edit(B,air(),new ServerBlockEdits.Cell(before.state(),relocated))));
            require(moved.committed() && moved.changed()==2,"cross-cube ownership transfer: "+moved);
            var current=access.snapshotIfLoaded(B).orElseThrow();
            var invalid=current.entityData();invalid.putString("id","minecraft:furnace");
            var bad=access.modifyServerAuthoritative(List.of(new ServerBlockEdits.Edit(A,air(),iron()),
                new ServerBlockEdits.Edit(B,current,new ServerBlockEdits.Cell(current.state(),invalid))));
            require(bad.status()==ServerBlockEdits.Status.INVALID_NBT && access.snapshotIfLoaded(A).orElseThrow().equals(air()),"invalid BE type has no partial write");
            var unknown=new BlockPos(12,8192,12);
            var unavailable=access.modifyServerAuthoritative(List.of(new ServerBlockEdits.Edit(A,air(),iron()),new ServerBlockEdits.Edit(unknown,air(),iron())));
            require(unavailable.status()==ServerBlockEdits.Status.UNAVAILABLE && access.readIfLoaded(unknown).isEmpty()
                && access.snapshotIfLoaded(A).orElseThrow().equals(air()),"unknown cube cannot be written or become fake air");
            var stale=current.entityData();stale.getList("Items",NbtElement.COMPOUND_TYPE).getCompound(0).putInt("count",38);
            var conflict=access.modifyServerAuthoritative(List.of(new ServerBlockEdits.Edit(A,air(),iron()),
                new ServerBlockEdits.Edit(B,new ServerBlockEdits.Cell(current.state(),stale),air())));
            require(conflict.status()==ServerBlockEdits.Status.CONFLICT && access.snapshotIfLoaded(A).orElseThrow().equals(air()),"stale inventory rejects entire batch");
        } else require(Files.isRegularFile(root.resolve("world-edits-write-ok.json")),"prior native write proof");
        require(access.snapshotIfLoaded(A).orElseThrow().equals(air()),"old ownership coordinate remains air");
        require(world.getBlockEntity(A)==null,"old tile entity absent");
        require(world.getBlockEntity(B) instanceof ChestBlockEntity,"new owner remains a chest");
        var chest=(ChestBlockEntity)world.getBlockEntity(B);
        require(chest.getName().getString().equals("cubic-moved-37") && chest.getStack(0).isOf(Items.DIAMOND)
            && chest.getStack(0).getCount()==37,"name and exact item count retained");
        require(drops==world.getEntitiesByClass(ItemEntity.class,new Box(A).union(new Box(B)).expand(.25),e->true).size(),"no scattered inventory duplicate");
        try {
            Files.writeString(root.resolve("world-edits-"+(reopen?"reopen":"write")+"-ok.json"),
                "{\"status\":\""+(reopen?"NATIVE_BATCH_AND_REOPEN_PASS":"NATIVE_BATCH_PASS_REOPEN_PENDING")
                +"\",\"seed\":271026,\"fromY\":2159,\"toY\":2160,\"diamonds\":37,\"noScatteredDuplicate\":true,\"readOnlyReopen\":"+reopen+"}\n");
        }catch(java.io.IOException failure){throw new IllegalStateException(failure);}
        System.out.println("CUBIC_WORLD_EDITS_PASS reopen="+reopen+" from="+A+" to="+B+" diamonds=37");
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
