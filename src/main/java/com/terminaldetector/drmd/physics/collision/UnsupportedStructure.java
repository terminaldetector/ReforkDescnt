package com.terminaldetector.drmd.physics.collision;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;

/** Loaded-world support query for the existing compound-body assembler, never a physics solver. */
public final class UnsupportedStructure {
    public enum Contact { EMPTY, MATERIAL, ANCHOR, UNKNOWN }
    public enum Status { DETACHED, EMPTY, ANCHORED, UNAVAILABLE, TOO_LARGE }
    public record Probe(Status status,Set<BodyTopology.Cell> cells) {
        public Probe { cells=Collections.unmodifiableSet(new LinkedHashSet<>(cells)); }
    }
    private UnsupportedStructure(){}

    /** Every face of the entire component must be known before any ownership transfer. */
    public static Probe examine(BodyTopology.Cell seed,Function<BodyTopology.Cell,Contact> read,int maxCells,int radius) {
        return examine(seed,read,(from,to)->true,maxCells,radius);
    }

    public static Probe examine(BodyTopology.Cell seed,Function<BodyTopology.Cell,Contact> read,
            BiPredicate<BodyTopology.Cell,BodyTopology.Cell> touching,int maxCells,int radius) {
        Objects.requireNonNull(seed);Objects.requireNonNull(read);
        if(maxCells<1||maxCells>256||radius<1||radius>16)throw new IllegalArgumentException("bounded structural query");
        var first=Objects.requireNonNull(read.apply(seed));
        if(first!=Contact.MATERIAL)return new Probe(first==Contact.UNKNOWN?Status.UNAVAILABLE:first==Contact.ANCHOR?Status.ANCHORED:Status.EMPTY,Set.of());
        var cells=new LinkedHashSet<BodyTopology.Cell>();
        var sampled=new HashMap<BodyTopology.Cell,Contact>();
        var pending=new ArrayDeque<BodyTopology.Cell>();
        cells.add(seed);sampled.put(seed,first);pending.add(seed);
        while(!pending.isEmpty()) {
            var from=pending.removeFirst();
            for(var next:from.neighbours()) {
                if(cells.contains(next))continue;
                var contact=sampled.computeIfAbsent(next,p->Objects.requireNonNull(read.apply(p)));
                if(contact==Contact.UNKNOWN)return new Probe(Status.UNAVAILABLE,cells);
                if(contact==Contact.EMPTY||!touching.test(from,next))continue;
                if(contact==Contact.ANCHOR)return new Probe(Status.ANCHORED,cells);
                if(Math.abs((long)next.x()-seed.x())>radius||Math.abs((long)next.y()-seed.y())>radius
                    ||Math.abs((long)next.z()-seed.z())>radius||cells.size()>=maxCells)
                    return new Probe(Status.TOO_LARGE,cells);
                cells.add(next);pending.addLast(next);
            }
        }
        return new Probe(Status.DETACHED,cells);
    }
}
