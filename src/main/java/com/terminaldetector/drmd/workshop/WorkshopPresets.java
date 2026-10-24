package com.terminaldetector.drmd.workshop;

import net.minecraft.nbt.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Map;

/** Named local construction + schema presets, separate from server world overrides. */
public final class WorkshopPresets {
    private WorkshopPresets() {}
    private static Path file(Path directory, String name) {
        if (name == null || !name.matches("[a-zA-Z0-9_-]{1,48}"))
            throw new IllegalArgumentException("Preset name: 1-48 letters, digits, _ or -");
        return directory.resolve(name + ".nbt");
    }
    public static void save(Path directory, String name, WeaponConfig config) throws IOException {
        Path target=file(directory,name); Files.createDirectories(directory);
        var root=new NbtCompound(); root.putInt("format",1); root.putString("target",config.targetWeaponId);
        root.put("modules",ClusterModule.listToNbt(config.clusters));
        root.put("weapon",encode(config.weapon)); root.put("projectile",encode(config.projectile));
        root.put("flak",encode(config.flak)); root.put("guidance",encode(config.guidance));
        Path temporary=Files.createTempFile(directory,"preset-",".tmp");
        try {
            NbtIo.writeCompressed(root,temporary);
            try { Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException e) { Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
    public static WeaponConfig load(Path directory,String name) throws IOException {
        var root=NbtIo.readCompressed(file(directory,name),NbtSizeTracker.of(1024*1024));
        if(root.getInt("format")!=1) throw new IOException("Unsupported preset format");
        var config=new WeaponConfig(); config.targetWeaponId=ConstructionRegistry.normalize(root.getString("target"));
        decode(root.getCompound("weapon"),config.weapon,WeaponSchema.WEAPON);
        decode(root.getCompound("projectile"),config.projectile,WeaponSchema.PROJECTILE);
        decode(root.getCompound("flak"),config.flak,WeaponSchema.FLAK);
        decode(root.getCompound("guidance"),config.guidance,WeaponSchema.GUIDANCE);
        config.clusters.clear(); config.clusters.addAll(ClusterModule.listFromNbt(root.getList("modules",NbtElement.COMPOUND_TYPE)));
        WeaponClusters.sort(config.clusters); return config;
    }
    private static NbtCompound encode(Map<String,Object> map) {
        var n=new NbtCompound(); map.forEach((k,v)->n.putString(k,String.valueOf(v))); return n;
    }
    private static void decode(NbtCompound n,Map<String,Object> map,List<WeaponSchema.Field> schema) throws IOException {
        for(var field:schema) if(n.contains(field.key())) {
            String s=n.getString(field.key());
            try { map.put(field.key(),switch(field.type()) {
                case INT -> Integer.parseInt(s); case FLOAT -> Float.parseFloat(s);
                case BOOL -> Boolean.parseBoolean(s); case STRING,ENUM -> s;
            }); } catch(NumberFormatException e) { throw new IOException("Invalid preset field: "+field.key(),e); }
        }
    }
}
