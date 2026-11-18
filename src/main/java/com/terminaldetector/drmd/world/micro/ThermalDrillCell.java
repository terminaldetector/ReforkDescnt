package com.terminaldetector.drmd.world.micro;

import net.minecraft.nbt.*;
import java.util.*;

/** Optional bounded source-preserving rim metadata, never an autonomous excavation job. */
public record ThermalDrillCell(int dose,int heat,long lastPulse,long coolAt,List<ThermalBore> bores) {
    public static final int COOL_TICKS=40,MAX_BORES=2;
    private static final Set<String> KEYS=Set.of("schema","dose","heat","lastPulse","coolAt","bores");
    private static final Set<String> BORE_KEYS=Set.of("ax","ay","az","bx","by","bz","radius");
    public ThermalDrillCell {
        bores=List.copyOf(bores);
        if(dose<0||dose>14||heat<0||heat>3||lastPulse<0||coolAt<lastPulse||coolAt-lastPulse>COOL_TICKS||bores.size()>MAX_BORES)throw new IllegalArgumentException("thermal cell bounds");
    }
    public static ThermalDrillCell cold(long now){return new ThermalDrillCell(0,0,Math.max(0,now-1),Math.max(0,now-1),List.of());}
    public ThermalDrillCell pulse(long now,float hardness,ThermalBore desired){
        if(now<=lastPulse)return this;
        int next=Math.min(14,(now>=coolAt?0:dose)+1),stage=next<ThermalBore.softenDose(hardness)?1:next<ThermalBore.meltDose(hardness)?2:3;
        var shapes=new ArrayList<>(bores);
        if(stage>=2){
            var bore=desired.withRadius(desired.radius()*(stage==2?.75:1));int same=-1;
            for(int i=0;i<shapes.size();i++)if(shapes.get(i).sameAxis(bore)){same=i;break;}
            if(same>=0){var previous=shapes.get(same);shapes.set(same,bore.withRadius(Math.max(previous.radius(),bore.radius())));}
            else if(shapes.size()<MAX_BORES)shapes.add(bore);
            else return this; // A third unrelated field is refused, never an unbounded ancestry list.
        }
        return new ThermalDrillCell(next,stage,now,now+COOL_TICKS,shapes);
    }
    public ThermalDrillCell cooled(){return new ThermalDrillCell(0,0,lastPulse,coolAt,bores);}
    public long mask(long before){for(var b:bores)before&=b.mask();return before;}
    public NbtCompound write(){
        var n=new NbtCompound();n.putInt("schema",1);n.putInt("dose",dose);n.putInt("heat",heat);n.putLong("lastPulse",lastPulse);n.putLong("coolAt",coolAt);var list=new NbtList();
        for(var b:bores){var q=new NbtCompound();q.putDouble("ax",b.ax());q.putDouble("ay",b.ay());q.putDouble("az",b.az());q.putDouble("bx",b.bx());q.putDouble("by",b.by());q.putDouble("bz",b.bz());q.putDouble("radius",b.radius());list.add(q);}n.put("bores",list);return n;
    }
    public static Optional<ThermalDrillCell> read(NbtElement raw){
        try{
            if(!(raw instanceof NbtCompound n)||!n.getKeys().equals(KEYS)||n.getInt("schema")!=1)return Optional.empty();
            for(String k:List.of("schema","dose","heat"))if(!n.contains(k,NbtElement.INT_TYPE))return Optional.empty();
            for(String k:List.of("lastPulse","coolAt"))if(!n.contains(k,NbtElement.LONG_TYPE))return Optional.empty();
            if(!(n.get("bores") instanceof NbtList list)||list.size()>MAX_BORES)return Optional.empty();var bores=new ArrayList<ThermalBore>();
            for(var e:list){if(!(e instanceof NbtCompound q)||!q.getKeys().equals(BORE_KEYS))return Optional.empty();for(String k:BORE_KEYS)if(!q.contains(k,NbtElement.DOUBLE_TYPE))return Optional.empty();bores.add(new ThermalBore(q.getDouble("ax"),q.getDouble("ay"),q.getDouble("az"),q.getDouble("bx"),q.getDouble("by"),q.getDouble("bz"),q.getDouble("radius")));}
            return Optional.of(new ThermalDrillCell(n.getInt("dose"),n.getInt("heat"),n.getLong("lastPulse"),n.getLong("coolAt"),bores));
        }catch(IllegalArgumentException malformed){return Optional.empty();}
    }
    public static int tint(int original,int heat){
        if(heat==0)return original;int target=switch(heat){case 1->0xff9b45;case 2->0xff681f;default->0xffd99a;};double mix=heat==1?.35:heat==2?.6:.8;
        int color=0;for(int shift:new int[]{0,8,16})color|=(int)Math.round(((original>>>shift)&255)*(1-mix)+((target>>>shift)&255)*mix)<<shift;return color;
    }
}
