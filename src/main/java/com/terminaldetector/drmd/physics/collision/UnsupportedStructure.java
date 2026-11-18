package com.terminaldetector.drmd.physics.collision;

import java.util.*;
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
        Objects.requireNonNull(seed);Objects.requireNonNull(read);
        if(maxCells<1||maxCells>256||radius<1||radius>16)throw new IllegalArgumentException("bounded structural query");
        var first=Objects.requireNonNull(read.apply(seed));
        if(first!=Contact.MATERIAL)return new Probe(first==Contact.UNKNOWN?Status.UNAVAILABLE:first==Contact.ANCHOR?Status.ANCHORED:Status.EMPTY,Set.of());
        var cells=new LinkedHashSet<BodyTopology.Cell>();var sampled=new HashSet<BodyTopology.Cell>();
        var pending=new ArrayDeque<BodyTopology.Cell>();cells.add(seed);sampled.add(seed);pending.add(seed);
        while(!pending.isEmpty())for(var next:pending.removeFirst().neighbours()) {
            if(!sampled.add(next))continue;
            var contact=Objects.requireNonNull(read.apply(next));
            if(contact==Contact.UNKNOWN)return new Probe(Status.UNAVAILABLE,cells);
            if(contact==Contact.ANCHOR)return new Probe(Status.ANCHORED,cells);
            if(contact!=Contact.MATERIAL)continue;
            if(Math.abs((long)next.x()-seed.x())>radius||Math.abs((long)next.y()-seed.y())>radius
                ||Math.abs((long)next.z()-seed.z())>radius||cells.size()>=maxCells)
                return new Probe(Status.TOO_LARGE,cells);
            cells.add(next);pending.addLast(next);
        }
        return new Probe(Status.DETACHED,cells);
    }
}
