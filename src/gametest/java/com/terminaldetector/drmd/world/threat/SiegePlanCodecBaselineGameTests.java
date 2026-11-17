package com.terminaldetector.drmd.world.threat;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.nbt.*;
import net.minecraft.test.*;
import java.util.UUID;
import static com.terminaldetector.drmd.world.threat.ThreatProgress.*;

/** Preimplementation protocol fixtures: no new plan class/API, actor or auto-raid.
 * Raw non-executable data roundtrips cannot be called natural combat acceptance. */
public final class SiegePlanCodecBaselineGameTests implements FabricGameTest {
    static final UUID PARTICIPANT=UUID.fromString("4232cf0c-8006-4c67-97d1-706e4eadf8f1"),
        PLAN=UUID.fromString("c08f398d-59d6-4d4a-89d6-21a038681a38");
    static NbtCompound legacy(){
        var state=new SurvivalThreatState();var point=new Point(18,140,-7);
        state.alarm(PARTICIPANT,UUID.fromString("8d792deb-27e8-482d-b8a4-a77064703bf9"),point,100);
        state.restorePlayer(PARTICIPANT,new ThreatProgress(new Snapshot(Phase.SEARCHING,GRACE_TICKS,500,2300,4100,100,-1,false,0,point,point)));
        if(!state.addActor(PARTICIPANT,UUID.fromString("036af97d-9e2f-4fa8-8615-0154218f92c7"),5000))throw new IllegalStateException("legacy fixture actor allowance");
        return state.writeNbt(new NbtCompound(),null);
    }
    static NbtCompound point(){var p=new NbtCompound();p.putDouble("x",18);p.putDouble("y",140);p.putDouble("z",-7);return p;}
    static NbtCompound observation(){
        var p=new NbtCompound();p.putUuid("site",UUID.fromString("131d0fcf-8865-46b7-a64a-e6e6a1a4d210"));
        p.putUuid("observer",UUID.fromString("346c54c1-6cf3-4f98-8336-7cb0955e2ea1"));p.putUuid("participant",PARTICIPANT);
        p.putString("world","minecraft:overworld");p.putLong("at",100);p.put("point",point());return p;
    }
    static NbtCompound record(){
        var p=new NbtCompound();p.putUuid("participant",PARTICIPANT);p.putString("faction",SurvivalThreatState.FACTION);p.putString("world","minecraft:overworld");
        p.putString("phase","PLANNED");p.putLong("proposedAt",100);p.putLong("clock",100);p.putLong("online",0);p.putLong("since",100);p.putLong("until",100);
        p.putLong("warningEnds",0);p.putLong("encounterEnds",0);p.putLong("cooldownEnds",0);
        p.put("origin",observation());p.put("known",observation());p.put("acks",new NbtList());return p;
    }
    static NbtCompound extension(){var n=new NbtCompound();n.putInt("schema",1);var plans=new NbtCompound();plans.put(PLAN.toString(),record());n.put("plans",plans);return n;}
    private static NbtCompound roundtrip(NbtCompound input,TestContext c){return SurvivalThreatState.fromNbt(input,c.getWorld().getRegistryManager()).writeNbt(new NbtCompound(),c.getWorld().getRegistryManager());}
    static void assertLegacy(TestContext c,NbtCompound expected,NbtCompound actual){
        var legacy=actual.copy();legacy.remove("siegePlans");
        c.assertTrue(legacy.equals(expected),"plan codec cannot change existing schema/players/actor allowance/cooldown/victim dedup values");
        var decoded=SurvivalThreatState.fromNbt(actual,c.getWorld().getRegistryManager());
        c.assertTrue(decoded.actorCount()==1&&decoded.rosterSize(PARTICIPANT)==1&&decoded.canDeploy(PARTICIPANT),"a non-autostart plan never reserves or consumes the second live recon slot");
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="siege-codec-missing")
    public void missingExtensionKeepsExactLegacyReconDefaults(TestContext c){
        var old=legacy();var out=roundtrip(old.copy(),c);assertLegacy(c,old,out);
        c.assertTrue(!out.contains("siegePlans"),"missing extension does not invent a plan or implicit activation");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="siege-codec-known")
    public void supportedNonExecutablePlanSurvivesRoundtripWithoutChangingRecon(TestContext c){
        var old=legacy();var input=old.copy();var ext=extension();input.put("siegePlans",ext.copy());var out=roundtrip(input,c);assertLegacy(c,old,out);
        c.assertTrue(out.getCompound("siegePlans").equals(ext),"supported non-autostart plan protocol must survive PersistentState codec roundtrip");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="siege-codec-opaque")
    public void futureExtensionRemainsOpaqueAndDoesNotResetLegacyProgress(TestContext c){
        var old=legacy();var input=old.copy();var ext=new NbtCompound();ext.putInt("schema",27);ext.putString("futurePolicy","opaque/non-executable");ext.putLongArray("payload",new long[]{9,3,8,5});input.put("siegePlans",ext.copy());
        var out=roundtrip(input,c);assertLegacy(c,old,out);
        c.assertTrue(out.getCompound("siegePlans").equals(ext),"unsupported plan subtree must be retained opaquely, never overwritten or activated");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="siege-codec-corrupt-neighbour")
    public void corruptNeighbourCannotEraseTheValidPlanOrExistingRecon(TestContext c){
        var old=legacy();var input=old.copy();var ext=extension();var bad=new NbtCompound();bad.putString("phase","not-a-plan");bad.putLong("arbitraryFutureValue",42);ext.getCompound("plans").put("malformed-id",bad);input.put("siegePlans",ext.copy());
        var out=roundtrip(input,c);assertLegacy(c,old,out);
        c.assertTrue(out.getCompound("siegePlans").getCompound("plans").getCompound(PLAN.toString()).equals(record()),"one corrupt neighbour cannot erase a separately valid intent");
        c.assertTrue(out.getCompound("siegePlans").getCompound("plans").getCompound("malformed-id").equals(bad),"unparsed corrupt record is preserved without gaining plan authority");c.complete();
    }
}
