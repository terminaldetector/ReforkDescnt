package com.terminaldetector.drmd.physics.ragdoll;

import com.terminaldetector.drmd.entity.RagdollEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.nbt.*;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import java.lang.ref.WeakReference;
import java.util.*;

/** Logical debris ownership/activity aggregation. Each physical entity, mass and drop stays owned. */
public final class RagdollDebrisGroups extends PersistentState {
    public static final int MAX_GROUPS=256,MAX_MEMBERS=4096,MAX_PER_GROUP=256;
    public record Member(UUID id,double mass,int parts,int life,boolean sleeping,String hull){}
    public record Snapshot(UUID id,String cause,long born,List<Member> members){
        public double mass(){return members.stream().mapToDouble(Member::mass).sum();}
        public int parts(){return members.stream().mapToInt(Member::parts).sum();}
        public long sleeping(){return members.stream().filter(Member::sleeping).count();}
    }
    private static final class Group {
        final UUID id;final String cause;final long born;final Map<UUID,Member> members=new LinkedHashMap<>();
        Group(UUID id,String cause,long born){this.id=id;this.cause=cause;this.born=born;}
        Snapshot snapshot(){return new Snapshot(id,cause,born,members.values().stream().sorted(Comparator.comparing(m->m.id().toString())).toList());}
    }
    private record Event(WeakReference<DamageSource> source,UUID group){}
    private static final class Events {long tick=Long.MIN_VALUE;final List<Event> values=new ArrayList<>();}
    private static final Map<ServerWorld,Events> EVENTS=new WeakHashMap<>();
    private final Map<UUID,Group> groups=new LinkedHashMap<>();
    private final Map<UUID,UUID> owners=new HashMap<>();
    private NbtCompound refused;
    public static RagdollDebrisGroups get(ServerWorld world){return world.getPersistentStateManager().getOrCreate(new Type<>(RagdollDebrisGroups::new,RagdollDebrisGroups::fromNbt,null),"drmd_ragdoll_groups");}
    public boolean available(){return refused==null;}
    /** One actual DamageSource instance in one ordinary tick is one causal death event. */
    public static UUID causeGroup(ServerWorld world,DamageSource source){
        var events=EVENTS.computeIfAbsent(world,w->new Events());
        if(events.tick!=world.getTime()){events.tick=world.getTime();events.values.clear();}
        for(var event:events.values)if(event.source().get()==source)return event.group();
        UUID id=UUID.randomUUID();if(events.values.size()<MAX_GROUPS)events.values.add(new Event(new WeakReference<>(source),id));return id;
    }
    public Optional<Snapshot> snapshot(UUID id){var group=groups.get(id);return group==null?Optional.empty():Optional.of(group.snapshot());}
    public List<Snapshot> snapshots(){return groups.values().stream().map(Group::snapshot).sorted(Comparator.comparing(g->g.id().toString())).toList();}
    public int memberCount(){return owners.size();}
    public boolean join(UUID groupId,String cause,long born,Member member){
        if(!available()||groupId==null||cause==null||cause.length()>128||born<0||!valid(member))return false;
        UUID old=owners.get(member.id());if(old!=null&&!old.equals(groupId))return false;
        var group=groups.get(groupId);
        if(group==null){if(groups.size()>=MAX_GROUPS||owners.size()>=MAX_MEMBERS)return false;group=new Group(groupId,cause,born);groups.put(groupId,group);}
        if(!group.members.containsKey(member.id())&&(group.members.size()>=MAX_PER_GROUP||owners.size()>=MAX_MEMBERS))return false;
        var previous=group.members.put(member.id(),member);owners.put(member.id(),groupId);if(!member.equals(previous))markDirty();return true;
    }
    public void destroyed(UUID groupId,UUID member){
        if(!available())return;var group=groups.get(groupId);if(group==null)return;
        if(group.members.remove(member)!=null){owners.remove(member);if(group.members.isEmpty())groups.remove(groupId);markDirty();}
    }
    public void wake(ServerWorld world,UUID groupId){
        if(!available())return;var group=groups.get(groupId);if(group==null)return;
        for(UUID member:List.copyOf(group.members.keySet()))if(world.getEntity(member) instanceof RagdollEntity body)body.wakeFromDebrisGroup();
    }
    private static boolean valid(Member m){return m!=null&&m.id()!=null&&Double.isFinite(m.mass())&&m.mass()>0&&m.parts()>0&&m.parts()<=RagdollSleepController.MAX_PARTS&&m.life()>=0&&m.life()<=1201&&m.hull()!=null&&m.hull().length()<=128;}
    public static RagdollDebrisGroups fromNbt(NbtCompound tag,RegistryWrapper.WrapperLookup lookup){
        var state=new RagdollDebrisGroups();
        if(!tag.contains("Schema",NbtElement.INT_TYPE)||tag.getInt("Schema")!=1){state.refused=tag.copy();return state;}
        try{
            if(!(tag.get("Groups") instanceof NbtList list)||!list.isEmpty()&&list.getHeldType()!=NbtElement.COMPOUND_TYPE||list.size()>MAX_GROUPS)throw new IllegalArgumentException("group list");
            for(int i=0;i<list.size();i++){
                var g=list.getCompound(i);UUID id=g.getUuid("Id");if(state.groups.containsKey(id)||!g.contains("Cause",NbtElement.STRING_TYPE)||!g.contains("Born",NbtElement.LONG_TYPE))throw new IllegalArgumentException("group identity");
                if(!(g.get("Members") instanceof NbtList members)||members.isEmpty()||members.getHeldType()!=NbtElement.COMPOUND_TYPE||members.size()>MAX_PER_GROUP)throw new IllegalArgumentException("member count");
                for(int j=0;j<members.size();j++){
                    var m=members.getCompound(j);
                    if(!m.contains("Mass",NbtElement.NUMBER_TYPE)||!m.contains("Parts",NbtElement.INT_TYPE)||!m.contains("Life",NbtElement.INT_TYPE)||!m.contains("Hull",NbtElement.STRING_TYPE)||!m.contains("Sleeping",NbtElement.BYTE_TYPE))throw new IllegalArgumentException("member fields");
                    var value=new Member(m.getUuid("Id"),m.getDouble("Mass"),m.getInt("Parts"),m.getInt("Life"),false,m.getString("Hull"));
                    if(state.owners.containsKey(value.id())||!state.join(id,g.getString("Cause"),g.getLong("Born"),value))throw new IllegalArgumentException("invalid member");
                }
            }
            return state;
        }catch(RuntimeException malformed){state.groups.clear();state.owners.clear();state.refused=tag.copy();return state;}
    }
    @Override public NbtCompound writeNbt(NbtCompound tag,RegistryWrapper.WrapperLookup lookup){
        if(refused!=null)return refused.copy();tag.putInt("Schema",1);var list=new NbtList();
        for(var group:snapshots()){
            var g=new NbtCompound();g.putUuid("Id",group.id());g.putString("Cause",group.cause());g.putLong("Born",group.born());var members=new NbtList();
            for(var m:group.members()){var value=new NbtCompound();value.putUuid("Id",m.id());value.putDouble("Mass",m.mass());value.putInt("Parts",m.parts());value.putInt("Life",m.life());value.putBoolean("Sleeping",m.sleeping());value.putString("Hull",m.hull());members.add(value);}
            g.put("Members",members);list.add(g);
        }
        tag.put("Groups",list);return tag;
    }
}
