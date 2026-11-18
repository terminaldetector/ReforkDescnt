package com.terminaldetector.drmd;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.*;

/** New controls; not part of the previously sealed82-case gate. */
public final class PlayerAaMixedBankGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="aa-material-future-mixed",tickLimit=300)
    public void futureOwnershipPausesAllMixedBanksWhileOrdinaryNeighborStillFires(TestContext c){PlayerAaMaterialFixture.run(c,PlayerAaMaterialFixture.Kind.FUTURE_MIXED);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="aa-material-corrupt-mixed",tickLimit=300)
    public void corruptOwnershipPausesAllMixedBanksWhileOrdinaryNeighborStillFires(TestContext c){PlayerAaMaterialFixture.run(c,PlayerAaMaterialFixture.Kind.CORRUPT_MIXED);}
    @GameTest(templateName=EMPTY_STRUCTURE,batchId="aa-material-empty-mixed",tickLimit=300)
    public void validEmptyOwnershipKeepsActualMixedBankLaserAndRocketCadence(TestContext c){PlayerAaMaterialFixture.run(c,PlayerAaMaterialFixture.Kind.EMPTY_MIXED);}
}
