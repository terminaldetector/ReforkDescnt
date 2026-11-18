package com.terminaldetector.drmd.assemblyprobe;

import com.terminaldetector.drmd.world.access.WorldAccess;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.*;

/** Opt-in GameTest-only observation/fault injection, bounded to one exact world
 * and at most sixteen explicitly owned cells on its server thread. */
public final class AssemblyWriteProbe {
    public record Event(String stage,BlockPos cell,String requested,String observed,int flags,Boolean result,List<String> stack){}
    private static Session active;
    private AssemblyWriteProbe(){}
    public static final class Session implements AutoCloseable {
        private final ServerWorld world;private final Set<BlockPos> cells;
        private final BlockPos denyClear,denyRestore;
        private final List<Event> events=new ArrayList<>();
        private boolean clearedDenied,restoreDenied,closed,truncated;
        private Session(ServerWorld world,Set<BlockPos> cells,BlockPos denyClear,BlockPos denyRestore){
            this.world=world;this.cells=Set.copyOf(cells);this.denyClear=denyClear;this.denyRestore=denyRestore;
        }
        public List<Event> events(){return List.copyOf(events);}
        public boolean clearDenied(){return clearedDenied;}
        public boolean restoreDenied(){return restoreDenied;}
        public boolean truncated(){return truncated;}
        @Override public void close(){if(!closed){closed=true;if(active==this)active=null;}}
        private void record(String stage,BlockPos cell,BlockState state,int flags,Boolean result){
            if(events.size()>=128){truncated=true;return;}
            var observed=WorldAccess.of(world).readIfLoaded(cell).map(Object::toString).orElse("UNAVAILABLE");
            var stack=StackWalker.getInstance().walk(frames->frames.limit(12)
                .map(f->f.getClassName()+"."+f.getMethodName()+":"+f.getLineNumber()).toList());
            events.add(new Event(stage,cell.toImmutable(),state.toString(),observed,flags,result,stack));
        }
    }
    public static Session open(ServerWorld world,Set<BlockPos> cells,BlockPos denyClear,BlockPos denyRestore){
        if(active!=null||!world.getServer().isOnThread()||cells.isEmpty()||cells.size()>16
            ||denyClear!=null&&!cells.contains(denyClear)||denyRestore!=null&&!cells.contains(denyRestore))
            throw new IllegalStateException("bounded exclusive test-world probe required");
        for(var cell:cells)if(WorldAccess.of(world).readIfLoaded(cell).isEmpty())throw new IllegalStateException("probe cannot fetch unavailable cells");
        return active=new Session(world,cells,denyClear,denyRestore);
    }
    public static boolean before(World world,BlockPos cell,BlockState state,int flags){
        var s=active;if(s==null||s.world!=world||s.closed||!s.cells.contains(cell))return false;
        if(!s.world.getServer().isOnThread())throw new IllegalStateException("probe server thread changed");
        s.record("HEAD",cell,state,flags,null);
        boolean deny=false;
        if(!s.clearedDenied&&cell.equals(s.denyClear)&&state.isAir()){s.clearedDenied=true;deny=true;}
        else if(s.clearedDenied&&!s.restoreDenied&&cell.equals(s.denyRestore)&&!state.isAir()){s.restoreDenied=true;deny=true;}
        if(deny)s.record("DENIED",cell,state,flags,false);return deny;
    }
    public static void after(World world,BlockPos cell,BlockState state,int flags,boolean result){
        var s=active;if(s!=null&&s.world==world&&!s.closed&&s.cells.contains(cell))s.record("RETURN",cell,state,flags,result);
    }
}
