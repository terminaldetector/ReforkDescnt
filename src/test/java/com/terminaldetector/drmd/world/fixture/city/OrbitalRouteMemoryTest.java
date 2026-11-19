package com.terminaldetector.drmd.world.fixture.city;

import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class OrbitalRouteMemoryTest {
    private static final CityBlueprint.Point ANCHOR=new CityBlueprint.Point(0,96,0);
    private static final List<OrbitalRouteBlueprint.Gate> GATES=OrbitalRouteBlueprint.gates(ANCHOR);
    private static OrbitalRouteMemory.Context context(UUID owner,UUID save,UUID job){return new OrbitalRouteMemory.Context(owner,save,job,"minecraft:overworld",71,"a".repeat(64),ANCHOR);}
    private static OrbitalRouteMemory.Context context(){return context(new UUID(1,2),new UUID(3,4),new UUID(5,6));}
    private static OrbitalRouteProgress.Position at(int i){var p=GATES.get(i).position();return new OrbitalRouteProgress.Position(p.x()+.5,p.y(),p.z()+.5);}
    private static NbtCompound saved(OrbitalRouteMemory memory){return memory.writeNbt(new NbtCompound(),null);}
    @Test void savedPartialCursorRestoresTheSameNextGateWithoutReplayingEarlierEntry(){
        var memory=new OrbitalRouteMemory();var context=context();memory.observe(context,GATES,at(0),10,true);memory.observe(context,GATES,at(1),20,true);
        var raw=saved(memory);var restored=OrbitalRouteMemory.read(raw,null);assertTrue(restored.available());assertEquals(2,restored.progress(context,GATES).state().passed());
        assertEquals(GATES.get(2),restored.progress(context,GATES).next());assertEquals(2,restored.observe(context,GATES,at(0),21,true).passed());assertEquals(raw,saved(restored));
    }
    @Test void foreignSaveJobOrWorldCannotResetOrInheritOriginalProgress(){
        var memory=new OrbitalRouteMemory();var context=context();memory.observe(context,GATES,at(0),10,true);var before=saved(memory);
        for(var other:List.of(context(context.owner(),new UUID(30,40),context.job()),context(context.owner(),context.save(),new UUID(50,60)),new OrbitalRouteMemory.Context(context.owner(),context.save(),context.job(),"minecraft:the_end",71,context.planHash(),ANCHOR)))
            assertThrows(IllegalArgumentException.class,()->memory.progress(other,GATES));
        assertEquals(before,saved(memory));assertEquals(0,memory.progress(context(new UUID(10,20),context.save(),context.job()),GATES).state().passed());
    }
    @Test void blockedWrongPositionAndRepeatedTickCannotCreateFalseSavedProgress(){
        var memory=new OrbitalRouteMemory();var context=context();var empty=saved(memory);
        assertEquals(0,memory.observe(context,GATES,at(0),10,false).passed());assertEquals(0,memory.observe(context,GATES,at(2),11,true).passed());assertEquals(0,memory.size());assertEquals(empty,saved(memory));
        memory.observe(context,GATES,at(0),12,true);assertEquals(1,memory.observe(context,GATES,at(1),12,true).passed());assertEquals(1,memory.size());
    }
    @Test void completionAndOriginalElapsedTimeSurviveCodecWithoutAnotherCompletion(){
        var memory=new OrbitalRouteMemory();var context=context();long tick=100;
        for(int i=0;i<GATES.size();i++){memory.observe(context,GATES,at(i),tick,true);tick+=20;}
        var before=saved(memory);var restored=OrbitalRouteMemory.read(before,null);var done=restored.progress(context,GATES).snapshot(tick);
        assertTrue(done.complete());assertEquals((GATES.size()-1)*20,done.elapsedTicks());assertEquals(done,restored.observe(context,GATES,at(0),100000,true));assertEquals(before,saved(restored));
    }
    @Test void futureOrMalformedSavedEnvelopeIsPreservedAndCannotMintAuthority(){
        var memory=new OrbitalRouteMemory();memory.observe(context(),GATES,at(0),10,true);var valid=saved(memory);
        var future=valid.copy();future.putInt("schema",77);var malformed=valid.copy();malformed.putString("runs","wrong type");
        for(var bad:List.of(future,malformed)){
            var quarantined=OrbitalRouteMemory.read(bad,null);assertFalse(quarantined.available());assertEquals(bad,saved(quarantined));assertThrows(IllegalStateException.class,()->quarantined.progress(context(),GATES));
        }
    }
    @Test void ownerBudgetRefusesTheNextCheckpointWithoutDiscardingPriorOwners(){
        var memory=new OrbitalRouteMemory();for(int i=0;i<64;i++)memory.observe(context(new UUID(0,i),new UUID(3,4),new UUID(5,6)),GATES,at(0),10,true);
        var before=saved(memory);assertThrows(IllegalStateException.class,()->memory.observe(context(new UUID(7,8),new UUID(3,4),new UUID(5,6)),GATES,at(0),11,true));assertEquals(64,memory.size());assertEquals(before,saved(memory));
    }
}
