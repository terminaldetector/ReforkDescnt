package com.terminaldetector.drmd.world.contraption;

import net.minecraft.block.*;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Bounded, registry-stable sparse snapshots. Empty space never costs a 4096-int cube.
 * The old cubic-volume format remains readable for existing saved worlds. */
public final class BodyShapeCodec {
    private BodyShapeCodec(){}
    public static NbtCompound write(List<BlockBodyEntity.Cell> cells) {
        if(cells.size()>BlockBodyEntity.MAX_BLOCKS)throw new IllegalArgumentException("body block budget");
        var tag=new NbtCompound();tag.putInt("format",2);
        var palette=new LinkedHashMap<BlockState,Integer>();var states=new int[cells.size()];var positions=new int[cells.size()];
        for(int i=0;i<cells.size();i++) {
            var c=cells.get(i);positions[i]=pack(c.pos());
            states[i]=palette.computeIfAbsent(c.state(),s->palette.size());
        }
        var entries=new NbtList();palette.keySet().forEach(s->entries.add(NbtHelper.fromBlockState(s)));
        tag.put("palette",entries);tag.putIntArray("positions",positions);tag.putIntArray("states",states);return tag;
    }
    public static List<BlockBodyEntity.Cell> read(NbtCompound tag) {
        if(tag.getInt("format")!=2)throw new IllegalArgumentException("unsupported body format");
        int[] positions=tag.getIntArray("positions"),states=tag.getIntArray("states");
        var entries=tag.getList("palette",NbtElement.COMPOUND_TYPE);
        if(positions.length>BlockBodyEntity.MAX_BLOCKS||positions.length!=states.length||entries.size()>2048)
            throw new IllegalArgumentException("body palette/cell budget");
        var palette=new ArrayList<BlockState>();
        for(int i=0;i<entries.size();i++) {
            var state=NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(),entries.getCompound(i));
            if(state.isAir()||!BlockBodyEntity.supports(state))throw new IllegalArgumentException("unsupported body material");
            palette.add(state);
        }
        var result=new ArrayList<BlockBodyEntity.Cell>();var seen=new HashSet<BlockPos>();
        for(int i=0;i<positions.length;i++) {
            var pos=unpack(positions[i]);int id=states[i];
            if(!seen.add(pos)||id<0||id>=palette.size())throw new IllegalArgumentException("duplicate body cell or invalid palette index");
            result.add(new BlockBodyEntity.Cell(pos,palette.get(id)));
        }
        return List.copyOf(result);
    }
    public static int pack(BlockPos pos) {
        if(!BlockBodyEntity.validLocal(pos))throw new IllegalArgumentException("body local coordinate");
        int offset=-BlockBodyEntity.MIN_LOCAL;
        return (pos.getX()+offset)|((pos.getY()+offset)<<8)|((pos.getZ()+offset)<<16);
    }
    public static BlockPos unpack(int packed) {
        int offset=-BlockBodyEntity.MIN_LOCAL;
        var pos=new BlockPos((packed&255)-offset,((packed>>>8)&255)-offset,((packed>>>16)&255)-offset);
        if((packed&0xff000000)!=0||!BlockBodyEntity.validLocal(pos))throw new IllegalArgumentException("body packed coordinate");
        return pos;
    }
}
