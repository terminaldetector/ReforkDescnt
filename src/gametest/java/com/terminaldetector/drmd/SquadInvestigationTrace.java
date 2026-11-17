package com.terminaldetector.drmd;

import com.terminaldetector.drmd.ai.*;
import net.minecraft.entity.ai.goal.GoalSelector;
import java.lang.reflect.Field;
import java.util.*;

/** Bounded test-only reads of actual goal/mail/navigation state. No replay or state edits. */
final class SquadInvestigationTrace {
    private static final boolean ENABLED=Boolean.getBoolean("drmd.debugSquadInvestigation");
    private static Object read(Object owner,String name)throws ReflectiveOperationException{
        var field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);
    }
    static void sample(DroneSquadTaskGameTests.Rig rig){
        if(!ENABLED||!rig.withScout||rig.ticks!=1&&rig.ticks%10!=0)return;
        var out=new LinkedHashMap<String,Object>();var drone=rig.wingman;
        out.put("tick",rig.ticks);out.put("worldTick",rig.c.getWorld().getTime());out.put("actor",drone.getUuid());
        out.put("position",drone.getPos());out.put("velocity",drone.getVelocity());out.put("yaw",drone.getYaw());out.put("pitch",drone.getPitch());
        out.put("ownTarget",drone.getTarget()==null?"none":drone.getTarget().getUuid());
        out.put("mail",DroneSquadCoordinator.message(drone));out.put("work",DroneSquadCoordinator.stats(rig.c.getWorld()));
        try{
            boolean found=false;
            for(Class<?> type=drone.getClass();type!=null;type=type.getSuperclass())for(Field field:type.getDeclaredFields()){
                if(field.getType()!=GoalSelector.class)continue;field.setAccessible(true);
                for(var wrapped:((GoalSelector)field.get(drone)).getGoals())if(wrapped.getGoal() instanceof SquadInvestigateGoal goal){
                    found=true;out.put("running",wrapped.isRunning());out.put("task",read(goal,"task"));out.put("nextRead",read(goal,"nextRead"));
                    var nav=read(goal,"navigation");
                    for(String name:List.of("previous","issued","destination","stalled","index","retryAt","expires","visibilityAt","blockedSight","sightRoute"))out.put(name,read(nav,name));
                    var route=(List<?>)read(nav,"route");out.put("routeSize",route.size());out.put("routeHead",route.subList(0,Math.min(12,route.size())));
                    int index=(Integer)read(nav,"index");if(index>=0&&index<route.size())out.put("currentCell",route.get(index));
                    if(read(nav,"search") instanceof BoundedFlightPath search){out.put("searchStatus",search.status());out.put("visited",search.visitedCells());}
                    else out.put("searchStatus","none");
                }
            }
            out.put("goalFound",found);
        }catch(ReflectiveOperationException error){throw new AssertionError("required read-only squad trace could not inspect own state",error);}
        System.out.println("SQUAD_TRACE "+out);
    }
}
