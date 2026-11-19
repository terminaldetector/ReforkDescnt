package com.terminaldetector.drmd.world.gravity;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GravityFieldIdsTest {
    @Test void cubicLayersThatShareVanillaPackedYKeepSeparateStableSourceIdentities() {
        var low = new BlockPos(-17, 0, 33);
        var high = low.add(0, 4096, 0);
        assertEquals(low.asLong(), high.asLong(), "actual vanilla packing reproduces the old collision premise");
        assertNotEquals(GravityFieldIds.torch(World.OVERWORLD, low), GravityFieldIds.torch(World.OVERWORLD, high));
        assertNotEquals(GravityFieldIds.torch(World.OVERWORLD, high), GravityFieldIds.torch(World.OVERWORLD, high.add(0,4096,0)));
        var below = low.add(0,-4096,0);
        assertNotEquals(GravityFieldIds.torch(World.OVERWORLD, low), GravityFieldIds.torch(World.OVERWORLD, below));
        assertEquals(GravityFieldIds.torch(World.OVERWORLD, high), GravityFieldIds.torch(World.OVERWORLD, high.toImmutable()));
        assertNotEquals(GravityFieldIds.torch(World.OVERWORLD, high), GravityFieldIds.torch(World.NETHER, high));
    }
    @Test void ordinarySavedCoordinatesKeepTheirHistoricalSourceIdentity() {
        for (int y : new int[]{-2048,-64,0,319,2047}) {
            var pos = new BlockPos(30000000,y,-30000000);
            var old = UUID.nameUUIDFromBytes((World.OVERWORLD.getValue()+"@"+pos.asLong()).getBytes(StandardCharsets.UTF_8));
            assertEquals(old,GravityFieldIds.torch(World.OVERWORLD,pos));
        }
    }
}
