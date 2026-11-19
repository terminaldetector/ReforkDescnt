package com.terminaldetector.drmd.world.planet;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Deterministic same-world navigation metadata, never a generator or a physics boundary. */
public final class PlanetaryZones {
    public static final int VERSION=1;
    public enum Zone {
        CORE("Core",0xffaa5544),MANTLE("Mantle",0xffb87544),UNDERGROUND("Underground",0xff778899),
        SURFACE("Surface",0xff66aa77),SKY("Sky",0xff66aacc),
        UPPER_ATMOSPHERE("Upper atmosphere",0xff8899cc),ORBIT("Orbit",0xffaa99dd);
        public final String label;
        public final int color;
        Zone(String label,int color){this.label=label;this.color=color;}
    }
    /** Actual dimension bounds and every band are half-open, with signed full-width Y. */
    public record Band(Zone zone,int bottom,int top){
        public Band{if(zone==null||bottom>=top)throw new IllegalArgumentException("invalid planetary band");}
        public boolean contains(double y){return Double.isFinite(y)&&y>=bottom&&y<top;}
    }
    public record Layout(int bottom,int top,int surfaceReference,List<Band> bands){
        public Layout{
            bands=List.copyOf(bands);
            if(bottom>=top||surfaceReference<bottom||surfaceReference>=top||bands.size()!=Zone.values().length)
                throw new IllegalArgumentException("actual bounds/surface reference required");
            int previous=bottom;
            for(int i=0;i<bands.size();i++){
                var band=bands.get(i);
                if(band.zone()!=Zone.values()[i]||band.bottom()!=previous||band.top()>top)
                    throw new IllegalArgumentException("planetary zones must cover actual bounds exactly once");
                previous=band.top();
            }
            if(previous!=top)throw new IllegalArgumentException("planetary zones leave an uncovered top");
        }
        public Optional<Zone> at(double y){
            if(!Double.isFinite(y)||y<bottom||y>=top)return Optional.empty();
            for(var band:bands)if(band.contains(y))return Optional.of(band.zone());
            throw new IllegalStateException("validated planetary coverage lost");
        }
    }
    private PlanetaryZones(){}

    /**
     * The surface reference comes from the running world's sea-level metadata.
     * Relative spans cap mission distances; they never override actual bounds,
     * claim generated terrain or revive legacy layer teleport/generation rules.
     * A dimension too small to represent the vocabulary stays explicitly unmapped.
     */
    public static Optional<Layout> fit(int bottom,int top,int surface){
        if(bottom>=top||surface<bottom||surface>=top)return Optional.empty();
        long depth=(long)surface-bottom,air=(long)top-surface;
        if(depth<4||air<4)return Optional.empty();
        long floor=(long)surface-Math.min(16,depth/4),below=floor-bottom;
        if(below<3)return Optional.empty();
        long[] cuts={bottom,floor-Math.min(384,below*2/3),floor-Math.min(128,below/3),floor,
            (long)surface+Math.min(64,air/4),(long)surface+Math.min(256,air/2),
            (long)surface+Math.min(1024,air*3/4),top};
        var bands=new ArrayList<Band>(Zone.values().length);
        for(int i=0;i<Zone.values().length;i++){
            if(cuts[i]>=cuts[i+1])return Optional.empty();
            bands.add(new Band(Zone.values()[i],Math.toIntExact(cuts[i]),Math.toIntExact(cuts[i+1])));
        }
        return Optional.of(new Layout(bottom,top,surface,bands));
    }
}
