package com.terminaldetector.drmd.physics.ragdoll;

import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RagdollDebrisGroupsTest {
    @Test void logicalGroupPersistsEveryIdentityMassPartAndRemainingLifeWithoutMergingContent(){
        var state=new RagdollDebrisGroups();var group=UUID.randomUUID();UUID[] ids={UUID.randomUUID(),UUID.randomUUID()};
        assertTrue(state.join(group,"explosion",123,new RagdollDebrisGroups.Member(ids[0],14,3,17,true,"jet")));
        assertTrue(state.join(group,"explosion",123,new RagdollDebrisGroups.Member(ids[1],6,1,18,false,"orb")));
        var saved=state.writeNbt(new NbtCompound(),null);var restored=RagdollDebrisGroups.fromNbt(saved,null);var snapshot=restored.snapshot(group).orElseThrow();
        assertEquals(20,snapshot.mass(),0);assertEquals(4,snapshot.parts());assertEquals(2,snapshot.members().size());
        assertEquals(0,snapshot.sleeping(),"saved sleep status never grants restored environmental authority");
        assertTrue(snapshot.members().stream().anyMatch(m->m.id().equals(ids[0])&&m.life()==17&&m.hull().equals("jet")));
        assertFalse(restored.join(UUID.randomUUID(),"other",124,new RagdollDebrisGroups.Member(ids[0],14,3,17,false,"jet")),"one member cannot be owned by two logical groups");
        restored.destroyed(group,ids[0]);assertEquals(6,restored.snapshot(group).orElseThrow().mass(),0);restored.destroyed(group,ids[1]);assertTrue(restored.snapshot(group).isEmpty());
    }
    @Test void groupBudgetsRefuseMetadataAdmissionWithoutSilentlyDeletingEarlierMembers(){
        var state=new RagdollDebrisGroups();var group=UUID.randomUUID();
        for(int i=0;i<RagdollDebrisGroups.MAX_PER_GROUP;i++)assertTrue(state.join(group,"blast",1,new RagdollDebrisGroups.Member(UUID.randomUUID(),1,1,0,false,"orb")));
        assertFalse(state.join(group,"blast",1,new RagdollDebrisGroups.Member(UUID.randomUUID(),1,1,0,false,"orb")));
        assertEquals(RagdollDebrisGroups.MAX_PER_GROUP,state.memberCount());
    }
    @Test void futureAndWrongTypedSchemasStayOpaqueAndCannotMutateGroups(){
        for(NbtElement version:new NbtElement[]{NbtInt.of(99),NbtString.of("1")}){
            var raw=new NbtCompound();raw.put("Schema",version);var extension=new NbtCompound();extension.putString("content","keep every field");raw.put("Future",extension);
            var state=RagdollDebrisGroups.fromNbt(raw,null);assertFalse(state.available());assertFalse(state.join(UUID.randomUUID(),"x",0,new RagdollDebrisGroups.Member(UUID.randomUUID(),1,1,0,false,"orb")));
            assertEquals(raw,state.writeNbt(new NbtCompound(),null));
        }
    }
}
