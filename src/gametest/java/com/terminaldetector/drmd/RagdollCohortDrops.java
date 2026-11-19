package com.terminaldetector.drmd;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import java.util.*;

/** Bounded fixture ownership of real born drops. Never moves, removes or creates a stack/entity. */
final class RagdollCohortDrops {
    static final int MAX_BIRTHS=256;
    final List<ItemEntity> births;final Map<String,Integer> original;
    private RagdollCohortDrops(List<ItemEntity> births){this.births=List.copyOf(births);original=Map.copyOf(counts());}
    static Set<UUID> identities(ServerWorld world){var ids=new HashSet<UUID>();for(var e:world.iterateEntities())if(e instanceof ItemEntity)ids.add(e.getUuid());return Set.copyOf(ids);}
    static RagdollCohortDrops afterActualDeaths(ServerWorld world,Set<UUID> before){
        var born=new ArrayList<ItemEntity>();for(var e:world.iterateEntities())if(e instanceof ItemEntity item&&!before.contains(item.getUuid())){if(born.size()>=MAX_BIRTHS)throw new IllegalStateException("actual cohort drop birth cap256");born.add(item);}
        return new RagdollCohortDrops(born);
    }
    Map<String,Integer> counts(){var counts=new TreeMap<String,Integer>();for(var item:births)if(!item.isRemoved()&&!item.getStack().isEmpty())counts.merge(net.minecraft.registry.Registries.ITEM.getId(item.getStack().getItem()).toString(),item.getStack().getCount(),Integer::sum);return counts;}
    List<Map<String,Object>> observations(Box birthArea){
        var rows=new ArrayList<Map<String,Object>>();for(var item:births)rows.add(Map.of("uuid",item.getUuid().toString(),"item",net.minecraft.registry.Registries.ITEM.getId(item.getStack().getItem()).toString(),"count",item.getStack().getCount(),"position",List.of(item.getX(),item.getY(),item.getZ()),"age",item.getItemAge(),"removed",item.isRemoved(),"removal",String.valueOf(item.getRemovalReason()),"outsideBirthArea",!birthArea.intersects(item.getBoundingBox())));return List.copyOf(rows);
    }
    void assertConserved(net.minecraft.test.TestContext context,Box birthArea){
        var actual=counts();System.out.println("RAGDOLL_OWNED_DROPS expected="+original+" actual="+actual+" births="+births.size()+" observations="+observations(birthArea));
        context.assertTrue(original.equals(actual),"owned actual drop content conserved across ordinary corpse cleanup, independent of migration; expected="+original+" actual="+actual);
    }
}
