package com.terminaldetector.drmd.world.fixture.city;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

/** Immutable save-bound plan context. Its file APIs are explicit worker I/O only. */
public record CityManifest(int schema,int blueprintVersion,int journalSchema,CityBatchJournal.Identity identity,
                           long worldSeed,long citySeed,CityBlueprint.Point anchor,CityBlueprint.Limits bounds,
                           Backend backend,int solidCount,int cutCount,Kind kind){
    public static final int SCHEMA=2,MAX_BYTES=16_384;
    private static final int MAGIC=0x4354594d;
    public enum Backend { STOCK,NATIVE_CUBIC }
    public enum Kind {
        CITY,ORBITAL_PILOT,ORBITAL_FIELD;
        public CityBlueprint.Plan create(long seed,CityBlueprint.Point anchor,CityBlueprint.Limits bounds){
            return switch(this){case CITY->CityBlueprint.create(seed,anchor,bounds);case ORBITAL_PILOT->OrbitalRouteBlueprint.create(seed,anchor,bounds);case ORBITAL_FIELD->OrbitalFieldBlueprint.create(seed,anchor,bounds);};
        }
    }
    public CityManifest(int schema,int blueprintVersion,int journalSchema,CityBatchJournal.Identity identity,long worldSeed,long citySeed,
                        CityBlueprint.Point anchor,CityBlueprint.Limits bounds,Backend backend,int solidCount,int cutCount){
        this(schema,blueprintVersion,journalSchema,identity,worldSeed,citySeed,anchor,bounds,backend,solidCount,cutCount,Kind.CITY);
    }
    public CityManifest{
        Objects.requireNonNull(identity);Objects.requireNonNull(anchor);Objects.requireNonNull(bounds);Objects.requireNonNull(backend);Objects.requireNonNull(kind);
        if((schema!=1&&schema!=SCHEMA)||schema==1&&kind!=Kind.CITY||blueprintVersion!=CityBlueprint.VERSION||journalSchema!=CityBatchJournal.VERSION)
            throw new IllegalArgumentException("unsupported city manifest/blueprint/journal version");
        if(solidCount<0||solidCount>CityBlueprint.MAX_SOLID||cutCount<0||cutCount>CityBlueprint.MAX_CUT)
            throw new IllegalArgumentException("city manifest material/cut budget");
    }
    public static CityManifest fromPlan(CityBatchJournal.Identity identity,long worldSeed,Backend backend,CityBlueprint.Plan plan){
        return fromPlan(identity,worldSeed,backend,plan,Kind.CITY);
    }
    public static CityManifest fromPlan(CityBatchJournal.Identity identity,long worldSeed,Backend backend,CityBlueprint.Plan plan,Kind kind){
        Objects.requireNonNull(plan);if(!identity.planHash().equals(plan.hash()))throw new IllegalArgumentException("city manifest plan identity mismatch");
        return new CityManifest(SCHEMA,CityBlueprint.VERSION,CityBatchJournal.VERSION,identity,worldSeed,plan.seed(),plan.anchor(),plan.limits(),backend,plan.solidCount(),plan.cutCount(),kind);
    }
    public CityBlueprint.Plan regenerate(){
        var plan=kind.create(citySeed,anchor,bounds);
        if(!plan.hash().equals(identity.planHash())||plan.solidCount()!=solidCount||plan.cutCount()!=cutCount)
            throw new IllegalArgumentException("city manifest geometry/count/hash mismatch");
        return plan;
    }
    public void requireContext(UUID actualSave,String dimension,long seed,CityBlueprint.Limits actualBounds,Backend actualBackend){
        if(!identity.saveId().equals(actualSave)||!identity.world().equals(dimension)||worldSeed!=seed||!bounds.equals(actualBounds)||backend!=actualBackend)
            throw new IllegalArgumentException("city save/dimension/seed/bounds/backend changed");
    }
    /** Never replaces existing or partially published metadata. */
    public void writeNew(Path file)throws IOException{
        regenerate();var bytes=new ByteArrayOutputStream();
        try(var out=new DataOutputStream(bytes)){
            out.writeInt(MAGIC);out.writeInt(schema);out.writeInt(blueprintVersion);out.writeInt(journalSchema);
            CityBatchJournal.writeIdentity(out,identity);out.writeLong(worldSeed);out.writeLong(citySeed);
            out.writeInt(anchor.x());out.writeInt(anchor.y());out.writeInt(anchor.z());
            out.writeInt(bounds.bottomY());out.writeInt(bounds.topY());out.writeInt(bounds.minX());out.writeInt(bounds.maxX());out.writeInt(bounds.minZ());out.writeInt(bounds.maxZ());
            out.writeByte(backend.ordinal());out.writeInt(solidCount);out.writeInt(cutCount);
            out.writeInt(CityBatchJournal.MAX_CELLS);out.writeInt(0);out.writeInt(0);
            if(schema>=2)out.writeUTF(kind.name());
        }
        CityDurableFiles.publishNew(file,CityDurableFiles.checked(bytes.toByteArray(),MAX_BYTES),MAX_BYTES);
    }
    /** Existing-file read only: no CREATE, provisioning, migration or automatic recovery. */
    public static CityManifest read(Path file)throws IOException{
        try(var in=new DataInputStream(new ByteArrayInputStream(CityDurableFiles.readChecked(file,MAX_BYTES)))){
            if(in.readInt()!=MAGIC)throw new IOException("invalid city manifest magic");
            int schema=in.readInt(),blueprint=in.readInt(),journal=in.readInt();var identity=CityBatchJournal.readIdentity(in);
            if(schema!=1&&schema!=SCHEMA)throw new IOException("unsupported city manifest schema");
            long worldSeed=in.readLong(),citySeed=in.readLong();var anchor=new CityBlueprint.Point(in.readInt(),in.readInt(),in.readInt());
            var bounds=new CityBlueprint.Limits(in.readInt(),in.readInt(),in.readInt(),in.readInt(),in.readInt(),in.readInt());int backend=in.readUnsignedByte();
            if(backend>=Backend.values().length)throw new IOException("unsupported city backend");
            int solid=in.readInt(),cut=in.readInt();
            if(in.readInt()!=CityBatchJournal.MAX_CELLS||in.readInt()!=0||in.readInt()!=0)
                throw new IOException("unsupported city batch/actor/entity budget or trailing data");
            Kind kind=schema==1?Kind.CITY:Kind.valueOf(in.readUTF());
            if(in.available()!=0)throw new IOException("trailing city manifest data");
            var manifest=new CityManifest(schema,blueprint,journal,identity,worldSeed,citySeed,anchor,bounds,Backend.values()[backend],solid,cut,kind);
            manifest.regenerate();return manifest;
        }catch(IllegalArgumentException invalid){throw new IOException("invalid city manifest",invalid);}
    }
}
