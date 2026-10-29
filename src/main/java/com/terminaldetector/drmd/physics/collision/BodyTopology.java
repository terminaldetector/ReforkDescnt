package com.terminaldetector.drmd.physics.collision;

import java.util.*;

/** Six-face local connectivity after atomic damage. Only newly broken connections split:
 * an unrelated edit cannot shatter pre-existing disconnected legacy blueprint cells.
 * No world, dimension, terrain load or per-block rigid body belongs to this algorithm. */
public final class BodyTopology {
    public record Cell(int x,int y,int z) {
        public List<Cell> neighbours(){return List.of(new Cell(x-1,y,z),new Cell(x+1,y,z),new Cell(x,y-1,z),new Cell(x,y+1,z),new Cell(x,y,z-1),new Cell(x,y,z+1));}
    }
    public record Result(List<Set<Cell>> detached,int visitedCells) {
        public Result{detached=detached.stream().map(s->Collections.unmodifiableSet(new HashSet<>(s))).toList();}
    }
    private static final Comparator<Cell> ORDER=Comparator.comparingInt(Cell::x).thenComparingInt(Cell::y).thenComparingInt(Cell::z);
    private BodyTopology(){}
    public static Result afterRemoval(Set<Cell> before,Set<Cell> after,Set<Cell> removed) {
        for(var cell:removed)if(!before.contains(cell)||after.contains(cell))throw new IllegalArgumentException("only actually removed structural cells belong to a damage batch");
        var pending=new HashSet<>(removed);var component=new HashMap<Cell,Integer>();var pieces=new ArrayList<Set<Cell>>();
        var relations=new ArrayList<Set<Integer>>();int visited=0;
        while(!pending.isEmpty()) {
            var boundary=new HashSet<Cell>();var queue=new ArrayDeque<Cell>();var first=pending.iterator().next();pending.remove(first);queue.add(first);
            while(!queue.isEmpty())for(var neighbour:queue.removeFirst().neighbours()) {
                if(pending.remove(neighbour))queue.addLast(neighbour);
                else if(before.contains(neighbour)&&after.contains(neighbour))boundary.add(neighbour);
            }
            if(boundary.size()<2)continue;
            var ids=new HashSet<Integer>();var remaining=new HashSet<>(boundary);
            while(!remaining.isEmpty()) {
                var seed=remaining.stream().min(ORDER).orElseThrow();Integer known=component.get(seed);
                if(known!=null){ids.add(known);remaining.removeAll(pieces.get(known));continue;}
                var seen=new HashSet<Cell>();queue.add(seed);seen.add(seed);boolean connected=false;
                while(!queue.isEmpty()) {
                    var cell=queue.removeFirst();visited++;remaining.remove(cell);
                    // Prove a connected boundary locally. A corner chip must not visit
                    // the other32760 cells of a solid that remains connected.
                    if(ids.isEmpty()&&remaining.isEmpty()){connected=true;break;}
                    for(var neighbour:cell.neighbours())if(after.contains(neighbour)&&seen.add(neighbour))queue.addLast(neighbour);
                }
                queue.clear();if(connected)break;
                int id=pieces.size();pieces.add(seen);ids.add(id);for(var cell:seen)component.put(cell,id);
            }
            if(ids.size()>1)relations.add(ids);
        }
        // Separate cut regions can break the same old component. Choose one survivor
        // for their combined relation, rather than retaining several overlapping bodies.
        int[] parent=new int[pieces.size()];for(int i=0;i<parent.length;i++)parent[i]=i;
        for(var relation:relations){int first=relation.iterator().next();for(int id:relation)parent[find(parent,id)]=find(parent,first);}
        var groups=new HashMap<Integer,Set<Integer>>();for(var relation:relations)for(int id:relation)groups.computeIfAbsent(find(parent,id),key->new HashSet<>()).add(id);
        var detached=new ArrayList<Set<Cell>>();
        for(var group:groups.values()) {
            int keeper=group.stream().min(Comparator.<Integer>comparingInt(id->-pieces.get(id).size()).thenComparing(id->pieces.get(id).stream().min(ORDER).orElseThrow(),ORDER)).orElseThrow();
            for(int id:group)if(id!=keeper)detached.add(pieces.get(id));
        }
        detached.sort(Comparator.comparing(set->set.stream().min(ORDER).orElseThrow(),ORDER));return new Result(detached,visited);
    }
    private static int find(int[] parent,int id){while(parent[id]!=id){parent[id]=parent[parent[id]];id=parent[id];}return id;}
}
