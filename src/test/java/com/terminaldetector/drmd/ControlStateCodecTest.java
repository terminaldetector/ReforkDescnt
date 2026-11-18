package com.terminaldetector.drmd;

import com.terminaldetector.drmd.world.mega.UfoControlSystem;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import java.util.HexFormat;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ControlStateCodecTest {
    private static final UUID BODY=UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
    private static final Identifier WORLD=Identifier.of("minecraft","overworld");
    // Legacy four-field packet: UUID16, VarInt300, true, UTF8 Identifier19.
    private static final String SKY="00112233445566778899aabbccddeeffac0201136d696e6563726166743a6f766572776f726c64";
    private static RegistryByteBuf buffer(){return new RegistryByteBuf(Unpooled.buffer(),DynamicRegistryManager.EMPTY);}
    private static byte[] bytes(RegistryByteBuf buffer){var out=new byte[buffer.readableBytes()];buffer.getBytes(buffer.readerIndex(),out);return out;}

    @Test void legacySkyRetainsTheExactFourArgumentConstructorAndGoldenWireBytes() throws Exception {
        var constructor=UfoControlSystem.State.class.getDeclaredConstructor(UUID.class,int.class,boolean.class,Identifier.class);
        var state=constructor.newInstance(BODY,300,true,WORLD);
        assertEquals(4,UfoControlSystem.State.class.getRecordComponents().length);
        var b=buffer();try {
            UfoControlSystem.State.CODEC.encode(b,state);assertEquals(SKY,HexFormat.of().formatHex(bytes(b)));
            assertEquals(state,UfoControlSystem.State.CODEC.decode(b));assertEquals(0,b.readableBytes());
        }finally{b.release();}
    }
    @Test void genericCapabilitiesHaveADistinctPayloadAndActualStrictRoundTrip() {
        var state=new UfoControlSystem.CoreState(BODY,300,true,WORLD,3,false);
        assertNotEquals(UfoControlSystem.State.ID,UfoControlSystem.CoreState.ID);
        var b=buffer();try {
            UfoControlSystem.CoreState.CODEC.encode(b,state);assertEquals(SKY+"0300",HexFormat.of().formatHex(bytes(b)));
            assertEquals(state,UfoControlSystem.CoreState.CODEC.decode(b));assertEquals(0,b.readableBytes());
        }finally{b.release();}
    }
    @Test void actualDecoderRefusesUnsupportedGenericMaskOrInventedTurretSupport() {
        for(var ending:new String[]{"0000","0700","0301"}){
            var b=buffer();try {
                b.writeBytes(HexFormat.of().parseHex(SKY+ending));
                assertThrows(IllegalArgumentException.class,()->UfoControlSystem.CoreState.CODEC.decode(b));
            }finally{b.release();}
        }
    }
}
