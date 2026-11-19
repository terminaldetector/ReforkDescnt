package com.terminaldetector.drmd.world.fixture.city;

import java.util.List;

/** Measured navigation, no teleport, reward or timer manipulation. State may be saved by its owner. */
public final class OrbitalRouteProgress {
    public record Position(double x,double y,double z){public Position{if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))throw new IllegalArgumentException("nonfinite route position");}}
    public record Snapshot(int passed,int total,long elapsedTicks,boolean complete){}
    public record State(int passed,long started,long last,long finished){
        public State{
            if(passed<0||passed>32||passed==0&&(started!=-1||last!=-1||finished!=-1)
                ||passed>0&&(started<0||last<started||finished!=-1&&finished!=last))throw new IllegalArgumentException("invalid route history");
        }
        public static State empty(){return new State(0,-1,-1,-1);}
    }
    private final List<OrbitalRouteBlueprint.Gate> gates;
    private int passed;private long started=-1,last=-1,finished=-1;
    public OrbitalRouteProgress(List<OrbitalRouteBlueprint.Gate> gates){this(gates,State.empty());}
    public OrbitalRouteProgress(List<OrbitalRouteBlueprint.Gate> gates,State saved){
        this.gates=List.copyOf(gates);if(gates.size()<2||gates.size()>32)throw new IllegalArgumentException("route gate cap2..32");
        if(saved.passed()>gates.size()||(saved.passed()==gates.size())!=(saved.finished()>=0))throw new IllegalArgumentException("route definition/history mismatch");
        passed=saved.passed();started=saved.started();last=saved.last();finished=saved.finished();
    }
    public State state(){return new State(passed,started,last,finished);}
    public OrbitalRouteBlueprint.Gate next(){return gates.get(Math.min(passed,gates.size()-1));}
    public double distance(Position position){var p=next().position();return Math.sqrt(squared(position,p));}
    private static double squared(Position p,CityBlueprint.Point at){double x=p.x-at.x()-.5,y=p.y-at.y(),z=p.z-at.z()-.5;return x*x+y*y+z*z;}
    public Snapshot observe(Position actual,long tick,boolean loadedClearance){
        if(tick<0)throw new IllegalArgumentException("negative server clock");
        if(passed==gates.size()||tick<=last||!loadedClearance||squared(actual,next().position())>2.25)return snapshot(tick);
        if(started<0)started=tick;last=tick;passed++;
        if(passed==gates.size())finished=tick;
        return snapshot(tick);
    }
    public Snapshot snapshot(long now){return new Snapshot(passed,gates.size(),started<0?0:Math.max(0,(finished>=0?finished:now)-started),passed==gates.size());}
}
