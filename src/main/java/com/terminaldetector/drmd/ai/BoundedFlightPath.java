package com.terminaldetector.drmd.ai;

import java.util.*;

/** Incremental six-axis A*: no world access, tickets, diagonal corner cutting or unbounded work. */
public final class BoundedFlightPath {
    public record Cell(int x,int y,int z) {
        public Cell offset(int x,int y,int z){return new Cell(Math.addExact(this.x,x),Math.addExact(this.y,y),Math.addExact(this.z,z));}
    }
    @FunctionalInterface public interface Space { boolean clear(Cell from,Cell to); }
    public enum Status { SEARCHING, FOUND, NO_ROUTE, LIMIT_REACHED }
    private record Node(int distance,Cell parent){}
    private record Entry(Cell cell,int distance,long score,long order){}
    private static final int[][] AXES={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
    private final Cell start,goal;
    private final int radius,nodeLimit;
    private final Space space;
    private final Map<Cell,Node> nodes=new HashMap<>();
    private final Set<Cell> closed=new HashSet<>();
    private final PriorityQueue<Entry> open=new PriorityQueue<>(Comparator.comparingLong(Entry::score).thenComparingLong(Entry::order));
    private Status status=Status.SEARCHING;
    private long order;
    private boolean trimmed;
    private List<Cell> path=List.of();
    public BoundedFlightPath(Cell start,Cell goal,int radius,int nodeLimit,Space space) {
        this.start=Objects.requireNonNull(start);this.goal=Objects.requireNonNull(goal);this.space=Objects.requireNonNull(space);
        if(radius<1||radius>48||nodeLimit<2||nodeLimit>4096)throw new IllegalArgumentException("flight search bounds");
        this.radius=radius;this.nodeLimit=nodeLimit;
        if(!inside(goal)){status=Status.LIMIT_REACHED;return;}
        nodes.put(start,new Node(0,null));open.add(new Entry(start,0,distance(start,goal),order++));
    }
    /** Returns queue pops consumed, including stale entries; at most six clearance calls per pop. */
    public int step(int budget) {
        if(budget<0||budget>128)throw new IllegalArgumentException("flight search work budget");
        int used=0;
        while(status==Status.SEARCHING&&used<budget&&!open.isEmpty()) {
            var entry=open.remove();used++;
            var node=nodes.get(entry.cell());if(closed.contains(entry.cell())||node.distance()!=entry.distance())continue;
            if(entry.cell().equals(goal)){finish();break;}
            closed.add(entry.cell());
            for(var axis:AXES) {
                Cell next;
                try{next=entry.cell().offset(axis[0],axis[1],axis[2]);}catch(ArithmeticException edge){continue;}
                if(!inside(next)||closed.contains(next))continue;
                var old=nodes.get(next);int cost=node.distance()+1;
                if(old!=null&&old.distance()<=cost)continue;
                if(old==null&&nodes.size()>=nodeLimit){trimmed=true;continue;}
                if(!space.clear(entry.cell(),next))continue;
                nodes.put(next,new Node(cost,entry.cell()));open.add(new Entry(next,cost,cost+distance(next,goal),order++));
            }
        }
        if(status==Status.SEARCHING&&open.isEmpty())status=trimmed?Status.LIMIT_REACHED:Status.NO_ROUTE;
        return used;
    }
    private boolean inside(Cell p){return Math.abs((long)p.x-start.x)<=radius&&Math.abs((long)p.y-start.y)<=radius&&Math.abs((long)p.z-start.z)<=radius;}
    private static long distance(Cell a,Cell b){return Math.abs((long)a.x-b.x)+Math.abs((long)a.y-b.y)+Math.abs((long)a.z-b.z);}
    private void finish(){var result=new ArrayList<Cell>();for(Cell at=goal;at!=null;at=nodes.get(at).parent())result.add(at);Collections.reverse(result);path=List.copyOf(result);status=Status.FOUND;open.clear();}
    public Status status(){return status;}
    public List<Cell> path(){return path;}
    public int visitedCells(){return nodes.size();}
}
