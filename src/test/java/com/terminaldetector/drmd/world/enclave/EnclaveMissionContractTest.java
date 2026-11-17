package com.terminaldetector.drmd.world.enclave;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.terminaldetector.drmd.world.enclave.EnclaveMissionContract.*;

class EnclaveMissionContractTest {
    private static final EnclaveMissionContract EXAMPLE=new EnclaveMissionContract("drmd:sample",1,"EXPLORE_SHAFT",
        "quest.drmd.explore_shaft",1,List.of(new Step(Type.ENTER_VOLUME,new Box(new Point(0,0,0),new Point(2,2,2)),null,null),
            new Step(Type.BREAK_BLOCK,null,new Point(3,0,0),"minecraft:iron_block")));
    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    @Test void canonicalRoundtripKeepsOrderHashAndSignedOffsets() {
        var json=EXAMPLE.toJson();var decoded=parse(bytes(json.toString()));
        assertEquals(EXAMPLE,decoded);assertEquals(EXAMPLE.hash(),decoded.hash());
        assertEquals(new Point(-97,-70_000,90),decoded.at(new Point(-100,-70_000,90)).get(1).block());
    }
    @Test void dataOnlyAlternateContractLoadsThroughTheSameCodec() throws Exception {
        for(var name:List.of("lab_infiltration","lab_side_entry")) {
            try(var stream=getClass().getResourceAsStream("/data/drmd/missions/"+name+".json")) {
                assertNotNull(stream);var decoded=parse(stream.readAllBytes());
                assertEquals("drmd:"+name,decoded.id());assertEquals(name.equals("lab_infiltration")?2:3,decoded.objectives().size());
            }
        }
    }
    @Test void duplicateFieldsWrongNumericTypesFractionsAndFutureSchemasRefuse() {
        String good=EXAMPLE.toJson().toString();
        for(var bad:List.of(good.replace("\"schema\":1","\"schema\":1,\"schema\":1"),
            good.replace("\"revision\":1","\"revision\":\"1\""),good.replace("\"difficulty\":1","\"difficulty\":1.5"),
            good.replace("\"schema\":1","\"schema\":2"),good.replace("\"reputation_reward\":3","\"reputation_reward\":300"),
            good.substring(0,good.length()-1)+",\"execute\":\"anything\"}"))
            assertThrows(RuntimeException.class,()->parse(bytes(bad)));
    }
    @Test void malformedUtf8SizeAndNestedPayloadsHaveFiniteRefusals() {
        assertThrows(IllegalArgumentException.class,()->parse(new byte[]{(byte)0xc3,0x28}));
        assertThrows(IllegalArgumentException.class,()->parse(new byte[MAX_BYTES+1]));
        assertThrows(IllegalArgumentException.class,()->parse(bytes("[".repeat(17)+"0"+"]".repeat(17))));
        assertThrows(IllegalArgumentException.class,()->parse(bytes(EXAMPLE.toJson()+" {}")));
    }
    @Test void duplicateMiningTargetsCannotConsumeTheSameSuccessfulBreakTwice() {
        assertThrows(IllegalArgumentException.class,()->new EnclaveMissionContract(EXAMPLE.id(),1,"EXPLORE_SHAFT",
            EXAMPLE.titleKey(),1,List.of(EXAMPLE.objectives().getFirst(),EXAMPLE.objectives().get(1),EXAMPLE.objectives().get(1))));
        assertThrows(IllegalArgumentException.class,()->new EnclaveMissionContract(EXAMPLE.id(),1,"EXPLORE_SHAFT",
            EXAMPLE.titleKey(),1,List.of(EXAMPLE.objectives().get(1),EXAMPLE.objectives().getFirst())));
    }
}
